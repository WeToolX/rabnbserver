// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract AiRWord_v3 {

    string public constant NAME = "AiRword Aion";
    string public constant SYMBOL = "AiRword";
    uint8 public constant DECIMALS = 18;
    uint256 public constant CAP = 210_000_000 * 10 ** uint256(DECIMALS);

    uint256 private constant MONTH = 1 minutes;
    uint256 private constant YEAR = 1 hours;
    uint256 private constant AMOUNT_SCALE = 10 ** 15;

    error BizError(uint8 code);

    uint8 private constant NOT_ADMIN = 1;
    uint8 private constant MINING_NOT_STARTED = 2;
    uint8 private constant INVALID_LOCK_TYPE = 3;
    uint8 private constant ORDER_ID_DUPLICATE = 4;
    uint8 private constant ANNUAL_BUDGET_EXCEEDED = 5;
    uint8 private constant EXCHANGE_TARGET_NOT_MET = 6;
    uint8 private constant NO_CLAIMABLE = 7;
    uint8 private constant ORDER_NOT_FOUND = 8;
    uint8 private constant NOT_AUTHORIZED = 9;
    uint8 private constant INVALID_DIST_TYPE = 10;
    uint8 private constant INVALID_GAS_PARAM = 11;
    uint8 private constant ZERO_AMOUNT = 12;
    uint8 private constant INVALID_ADDRESS = 13;
    uint8 private constant CAP_EXCEEDED = 14;
    uint8 private constant INSUFFICIENT_BALANCE = 15;
    uint8 private constant INSUFFICIENT_ALLOWANCE = 16;
    uint8 private constant BATCH_LIMIT_EXCEEDED = 17;
    uint8 private constant EMPTY_BATCH = 18;
    uint8 private constant LENGTH_MISMATCH = 19;

    address public immutable owner;
    address public admin;
    address public immutable community;

    uint256 private _totalSupply;

    mapping(address => uint256) private _balances;

    mapping(address => mapping(address => uint256)) private _allowances;

    uint256 public miningStart;
    uint256 public lastSettledYear;
    uint256 public yearBudget;
    uint256 public yearMinted;
    uint256 public remainingCap;
    uint256 public yearStartTs;

    uint256 public maxScanLimit = 100;
    uint256 public maxBatchLimit = 1000;

    struct LockRecord {
        uint256 time;
        uint256 amount;
        bool claimStatus;
        bool fragmentStatus;
    }

    struct ClaimAllState {
        uint256 cursorStart;
        uint256 i;
        uint256 processed;
        uint256 claimable;
    }

    struct BatchData {
        uint256 orderId;
        uint256 l1;
        uint256 l2;
        uint256 l3;
        uint256 direct;
    }

    struct LockStats {
        uint256 totalCount;
        uint256 totalAmount;
        uint256 claimableCount;
        uint256 claimableAmount;
        uint256 unmaturedCount;
        uint256 unmaturedAmount;
        uint256 claimedCount;
        uint256 claimedAmount;
        uint256 fragmentedCount;
        uint256 fragmentedAmount;
        uint256 earliestUnlockTime;
        uint256 latestUnlockTime;
        uint256 lastIndex;
    }

    struct PreviewClaimable {
        uint256 claimable;
        uint256 burnAmount;
        uint256 netAmount;
        uint256 processed;
        uint256 nextCursor;
    }

    enum OrderMethodType {
        ALLOCATE,
        CLAIM,
        EXCHANGE_LOCKED,
        EXCHANGE_UNLOCKED
    }

    struct OrderRecord {
        OrderMethodType methodType;
        uint8 lockType;
        uint256 amount;
        uint256 executedAmount;
        uint256 netAmount;
        uint256 burnAmount;
        uint256 timestamp;
    }

    mapping(address => mapping(uint8 => LockRecord[])) private _locks;

    mapping(address => mapping(uint8 => mapping(uint8 => uint256))) private _cursors;

    mapping(address => mapping(uint256 => OrderRecord)) private _orders;
    mapping(address => mapping(uint256 => bool)) private _orderExists;

    mapping(address => mapping(address => bool)) private _operatorApproved;

    uint8 private constant MODE_CLAIM = 0;
    uint8 private constant MODE_FRAG_LOCKED = 1;
    uint8 private constant MODE_FRAG_UNLOCKED = 2;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    event UnmintedAllocationBurned(uint256 year, uint256 unminted);

    event ClaimWithBurn(
        address indexed user,
        uint256 claimable,
        uint256 burnAmount,
        uint256 netAmount,
        uint8 lockType
    );

    event AllocateDetail(
        address indexed user,
        uint256 indexed orderId,
        uint8 lockType,
        uint8 distType,
        uint256 amount
    );

    constructor(address admin_, address communityAddress) {
        if (admin_ == address(0) || communityAddress == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }

        owner = msg.sender;
        admin = admin_;
        community = communityAddress;

        uint256 toContract = (CAP * 4) / 100;
        uint256 toCommunity = (CAP * 6) / 100;
        _mint(address(this), toContract);
        _mint(communityAddress, toCommunity);

        remainingCap = CAP - toContract - toCommunity;
    }

    function name() external pure returns (string memory) {
        return NAME;
    }

    function symbol() external pure returns (string memory) {
        return SYMBOL;
    }

    function decimals() external pure returns (uint8) {
        return DECIMALS;
    }

    function totalSupply() external view returns (uint256) {
        return _totalSupply;
    }

    function balanceOf(address account) external view returns (uint256) {
        return _balances[account];
    }

    function allowance(address owner_, address spender) external view returns (uint256) {
        return _allowances[owner_][spender];
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        _transfer(msg.sender, to, amount);
        return true;
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        _approve(msg.sender, spender, amount);
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        uint256 current = _allowances[from][msg.sender];
        if (current < amount) {
            revert BizError(INSUFFICIENT_ALLOWANCE);
        }
        _approve(from, msg.sender, current - amount);
        _transfer(from, to, amount);
        return true;
    }

    function setAdmin(address newAdmin) external {

        if (msg.sender != owner) {
            revert BizError(NOT_ADMIN);
        }
        if (newAdmin == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        admin = newAdmin;
    }

    function approveOperator(address operator, bool approved) external {

        _operatorApproved[msg.sender][operator] = approved;
    }

    function isOperatorApproved(address user, address operator) external view returns (bool) {
        return _operatorApproved[user][operator];
    }

    function setMaxScanLimit(uint256 limit) external onlyAdmin {
        maxScanLimit = limit;
    }

    function getMaxScanLimit() external view returns (uint256) {
        return maxScanLimit;
    }

    function setMaxBatchLimit(uint256 limit) external onlyAdmin {
        maxBatchLimit = limit;
    }

    function getMaxBatchLimit() external view returns (uint256) {
        return maxBatchLimit;
    }

    function estimateMaxCount(uint256 perRecordGas, uint256 fixedGas) external view returns (uint256) {

        if (perRecordGas == 0 || fixedGas == 0) {
            revert BizError(INVALID_GAS_PARAM);
        }
        uint256 gasLimit = block.gaslimit;
        if (gasLimit <= fixedGas) {
            return 0;
        }
        uint256 suggested = (gasLimit - fixedGas) / perRecordGas;
        if (suggested > maxScanLimit) {
            return maxScanLimit;
        }
        return suggested;
    }

    function startMining() external onlyAdmin {

        if (miningStart != 0) {
            return;
        }
        miningStart = block.timestamp;
        yearStartTs = miningStart;
        yearBudget = remainingCap / 2;
        yearMinted = 0;
        lastSettledYear = 0;
    }

    function settleToCurrentYear() public {
        _requireStarted();
        uint256 currentYear = _currentYear();
        while (lastSettledYear + 1 < currentYear) {
            uint256 unminted = yearBudget - yearMinted;
            remainingCap -= unminted;
            emit UnmintedAllocationBurned(lastSettledYear + 1, unminted);
            lastSettledYear += 1;
            yearBudget = remainingCap / 2;
            yearMinted = 0;
            yearStartTs = miningStart + (lastSettledYear * YEAR);
        }
    }

    function getTodayMintable() external view returns (uint256) {
        _requireStartedView();
        (uint256 budget, uint256 minted, uint256 startTs) = _simulateToCurrentYear();
        uint256 yearRemaining = budget - minted;
        uint256 daysPassed = (block.timestamp - startTs) / 1 days;
        if (daysPassed >= 365) {
            return 0;
        }
        uint256 daysRemaining = 365 - daysPassed;
        return yearRemaining / daysRemaining;
    }

    function getCurrentYearRemaining()
    external
    view
    returns (uint256 yearRemaining, uint256 budget, uint256 minted)
    {
        _requireStartedView();
        (uint256 b, uint256 m, ) = _simulateToCurrentYear();
        return (b - m, b, m);
    }

    function allocateEmissionToLocks(
        address to,
        uint256 amount,
        uint8 lockType,
        uint8 distType,
        uint256 orderId
    ) external onlyAdmin {
        _requireStarted();

        if (to == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        if (amount == 0) {
            revert BizError(ZERO_AMOUNT);
        }
        if (distType != 1 && distType != 2) {
            revert BizError(INVALID_DIST_TYPE);
        }
        if (distType == 2) {
            if (lockType != 0) {
                revert BizError(INVALID_LOCK_TYPE);
            }
        } else {
            if (!_isValidLockType(lockType)) {
                revert BizError(INVALID_LOCK_TYPE);
            }
        }

        _requireOrderNotExists(to, orderId);

        settleToCurrentYear();

        if (yearMinted + amount > yearBudget) {
            revert BizError(ANNUAL_BUDGET_EXCEEDED);
        }

        yearMinted += amount;
        remainingCap -= amount;

        if (distType == 1) {
            _pushLock(to, lockType, amount);
        } else {

            _mint(to, amount);
        }
        emit AllocateDetail(to, orderId, lockType, distType, amount);

        OrderRecord storage r = _orders[to][orderId];
        r.methodType = OrderMethodType.ALLOCATE;
        r.lockType = lockType;
        r.amount = amount;
        r.executedAmount = amount;
        r.netAmount = (distType == 2) ? amount : 0;
        r.burnAmount = 0;
        r.timestamp = block.timestamp;
        _orderExists[to][orderId] = true;
    }

    function allocateEmissionToLocksBatch(address[] calldata tos, BatchData[] calldata data) external onlyAdmin {
        _requireStarted();
        if (tos.length != data.length) {
            revert BizError(LENGTH_MISMATCH);
        }
        if (tos.length == 0) {
            revert BizError(EMPTY_BATCH);
        }
        if (tos.length > maxBatchLimit) {
            revert BizError(BATCH_LIMIT_EXCEEDED);
        }

        settleToCurrentYear();

        for (uint256 i = 0; i < tos.length; i++) {
            address to = tos[i];
            if (to == address(0)) {
                revert BizError(INVALID_ADDRESS);
            }

            BatchData calldata d = data[i];
            uint256 orderId = d.orderId;
            _requireOrderNotExists(to, orderId);

            uint256 l1Amount = _scaleAmount(d.l1);
            uint256 l2Amount = _scaleAmount(d.l2);
            uint256 l3Amount = _scaleAmount(d.l3);
            uint256 directAmount = _scaleAmount(d.direct);
            uint256 totalAmount = l1Amount + l2Amount + l3Amount + directAmount;

            if (totalAmount == 0) {
                continue;
            }

            if (yearMinted + totalAmount > yearBudget) {
                revert BizError(ANNUAL_BUDGET_EXCEEDED);
            }
            yearMinted += totalAmount;
            remainingCap -= totalAmount;

            _allocateOne(to, orderId, 1, 1, l1Amount);
            _allocateOne(to, orderId, 2, 1, l2Amount);
            _allocateOne(to, orderId, 3, 1, l3Amount);
            _allocateOne(to, orderId, 0, 2, directAmount);

            OrderRecord storage r = _orders[to][orderId];
            r.methodType = OrderMethodType.ALLOCATE;
            r.lockType = 0;
            r.amount = totalAmount;
            r.executedAmount = totalAmount;
            r.netAmount = directAmount;
            r.burnAmount = 0;
            r.timestamp = block.timestamp;
            _orderExists[to][orderId] = true;
        }
    }

    function claimAll(address user, uint8 lockType, uint256 orderId) external onlyAdmin returns (uint256) {
        _requireStarted();
        if (!_isValidLockType(lockType)) {
            revert BizError(INVALID_LOCK_TYPE);
        }
        if (!_operatorApproved[user][msg.sender]) {
            revert BizError(NOT_AUTHORIZED);
        }
        _requireOrderNotExists(user, orderId);

        settleToCurrentYear();

        ClaimAllState memory s;
        s.cursorStart = _cursors[user][lockType][MODE_CLAIM];
        LockRecord[] storage list = _locks[user][lockType];
        uint256 len = list.length;
        s.i = s.cursorStart;

        while (s.i < len && s.processed < maxScanLimit) {
            s.processed++;
            LockRecord storage rec = list[s.i];
            if (rec.time > block.timestamp) {

                break;
            }

            if (rec.claimStatus || rec.fragmentStatus) {

                s.i++;
                continue;
            }

            rec.claimStatus = true;
            s.claimable += rec.amount;
            s.i++;
        }

        _cursors[user][lockType][MODE_CLAIM] = s.i;

        if (s.claimable == 0) {
            if (s.i == s.cursorStart) {
                revert BizError(NO_CLAIMABLE);
            }
            OrderRecord storage r0 = _orders[user][orderId];
            r0.methodType = OrderMethodType.CLAIM;
            r0.lockType = lockType;
            r0.amount = 0;
            r0.executedAmount = 0;
            r0.netAmount = 0;
            r0.burnAmount = 0;
            r0.timestamp = block.timestamp;
            _orderExists[user][orderId] = true;
            return 0;
        }

        uint256 burnAmount = _calcBurn(lockType, s.claimable);
        uint256 netAmount = s.claimable - burnAmount;

        _mint(address(this), s.claimable);
        if (burnAmount > 0) {
            _burn(address(this), burnAmount);
        }
        _transfer(address(this), user, netAmount);

        emit ClaimWithBurn(user, s.claimable, burnAmount, netAmount, lockType);

        OrderRecord storage r = _orders[user][orderId];
        r.methodType = OrderMethodType.CLAIM;
        r.lockType = lockType;
        r.amount = 0;
        r.executedAmount = s.claimable;
        r.netAmount = netAmount;
        r.burnAmount = burnAmount;
        r.timestamp = block.timestamp;
        _orderExists[user][orderId] = true;

        return netAmount;
    }

    function exchangeLockedFragment(
        address user,
        uint8 lockType,
        uint256 targetAmount,
        uint256 orderId
    ) external onlyAdmin returns (uint256) {
        _requireStarted();
        if (!_isValidLockType(lockType)) {
            revert BizError(INVALID_LOCK_TYPE);
        }
        if (!_operatorApproved[user][msg.sender]) {
            revert BizError(NOT_AUTHORIZED);
        }
        _requireOrderNotExists(user, orderId);
        if (targetAmount == 0) {
            revert BizError(EXCHANGE_TARGET_NOT_MET);
        }

        settleToCurrentYear();

        uint256 cursor = _cursors[user][lockType][MODE_FRAG_LOCKED];
        LockRecord[] storage list = _locks[user][lockType];
        uint256 len = list.length;
        uint256 i = cursor;
        uint256 processed = 0;
        uint256 sum = 0;

        while (i < len && processed < maxScanLimit) {
            processed++;
            LockRecord storage rec = list[i];

            if (rec.time > block.timestamp) {

                if (rec.fragmentStatus) {
                    i++;
                    continue;
                }
                if (rec.claimStatus) {
                    break;
                }

                rec.fragmentStatus = true;
                sum += rec.amount;
                i++;
                if (sum >= targetAmount) {
                    break;
                }
                continue;
            }

            if (rec.claimStatus) {
                break;
            }
            if (rec.fragmentStatus) {
                i++;
                continue;
            }

            i++;
        }

        _cursors[user][lockType][MODE_FRAG_LOCKED] = i;

        if (sum < targetAmount) {
            revert BizError(EXCHANGE_TARGET_NOT_MET);
        }

        _mint(address(this), sum);
        _burn(address(this), sum);

        OrderRecord storage r = _orders[user][orderId];
        r.methodType = OrderMethodType.EXCHANGE_LOCKED;
        r.lockType = lockType;
        r.amount = targetAmount;
        r.executedAmount = sum;
        r.netAmount = 0;
        r.burnAmount = sum;
        r.timestamp = block.timestamp;
        _orderExists[user][orderId] = true;

        return sum;
    }

    function exchangeUnlockedFragment(
        address user,
        uint8 lockType,
        uint256 targetAmount,
        uint256 orderId
    ) external onlyAdmin returns (uint256) {
        _requireStarted();
        if (!_isValidLockType(lockType)) {
            revert BizError(INVALID_LOCK_TYPE);
        }
        if (!_operatorApproved[user][msg.sender]) {
            revert BizError(NOT_AUTHORIZED);
        }
        _requireOrderNotExists(user, orderId);
        if (targetAmount == 0) {
            revert BizError(EXCHANGE_TARGET_NOT_MET);
        }

        settleToCurrentYear();

        uint256 cursor = _cursors[user][lockType][MODE_FRAG_UNLOCKED];
        LockRecord[] storage list = _locks[user][lockType];
        uint256 len = list.length;
        uint256 i = cursor;
        uint256 processed = 0;
        uint256 sum = 0;

        while (i < len && processed < maxScanLimit) {
            processed++;
            LockRecord storage rec = list[i];

            if (rec.time > block.timestamp) {

                break;
            }

            if (rec.claimStatus) {
                break;
            }
            if (rec.fragmentStatus) {
                break;
            }

            rec.fragmentStatus = true;
            sum += rec.amount;
            i++;
            if (sum >= targetAmount) {
                break;
            }
        }

        _cursors[user][lockType][MODE_FRAG_UNLOCKED] = i;

        if (sum < targetAmount) {
            revert BizError(EXCHANGE_TARGET_NOT_MET);
        }

        _mint(address(this), sum);
        _burn(address(this), sum);

        OrderRecord storage r = _orders[user][orderId];
        r.methodType = OrderMethodType.EXCHANGE_UNLOCKED;
        r.lockType = lockType;
        r.amount = targetAmount;
        r.executedAmount = sum;
        r.netAmount = 0;
        r.burnAmount = sum;
        r.timestamp = block.timestamp;
        _orderExists[user][orderId] = true;

        return sum;
    }

    function previewClaimable(address user, uint8 lockType) external view returns (PreviewClaimable memory) {
        _requireStartedView();
        if (!_isValidLockType(lockType)) {
            revert BizError(INVALID_LOCK_TYPE);
        }

        uint256 cursor = _cursors[user][lockType][MODE_CLAIM];
        LockRecord[] storage list = _locks[user][lockType];
        uint256 len = list.length;
        uint256 i = cursor;
        uint256 processed = 0;
        uint256 claimable = 0;

        while (i < len && processed < maxScanLimit) {
            processed++;
            LockRecord storage rec = list[i];
            if (rec.time > block.timestamp) {
                break;
            }
            if (rec.claimStatus || rec.fragmentStatus) {
                i++;
                continue;
            }
            claimable += rec.amount;
            i++;
        }

        uint256 burnAmount = _calcBurn(lockType, claimable);
        uint256 netAmount = claimable - burnAmount;

        return PreviewClaimable({
            claimable: claimable,
            burnAmount: burnAmount,
            netAmount: netAmount,
            processed: processed,
            nextCursor: i
        });
    }

    function getLockStats(address user, uint8 lockType) external view returns (LockStats memory) {
        _requireStartedView();
        if (!_isValidLockType(lockType)) {
            revert BizError(INVALID_LOCK_TYPE);
        }

        LockRecord[] storage list = _locks[user][lockType];
        uint256 len = list.length;

        LockStats memory stats;
        stats.totalCount = len;
        stats.lastIndex = (len == 0) ? 0 : (len - 1);

        for (uint256 i = 0; i < len; i++) {
            LockRecord storage rec = list[i];
            stats.totalAmount += rec.amount;

            if (rec.fragmentStatus) {

                stats.fragmentedCount += 1;
                stats.fragmentedAmount += rec.amount;
            } else if (rec.time <= block.timestamp) {

                if (rec.claimStatus) {
                    stats.claimedCount += 1;
                    stats.claimedAmount += rec.amount;
                } else {
                    stats.claimableCount += 1;
                    stats.claimableAmount += rec.amount;
                }
            } else {

                stats.unmaturedCount += 1;
                stats.unmaturedAmount += rec.amount;
            }

            if (!rec.fragmentStatus && rec.time > block.timestamp) {
                if (stats.earliestUnlockTime == 0 || rec.time < stats.earliestUnlockTime) {
                    stats.earliestUnlockTime = rec.time;
                }
                if (rec.time > stats.latestUnlockTime) {
                    stats.latestUnlockTime = rec.time;
                }
            }
        }

        return stats;
    }

    function getLockStatsPaged(
        address user,
        uint8 lockType,
        uint256 cursor
    )
    external
    view
    returns (LockStats memory stats, uint256 nextCursor, uint256 processed, bool finished)
    {
        _requireStartedView();
        if (!_isValidLockType(lockType)) {
            revert BizError(INVALID_LOCK_TYPE);
        }

        LockRecord[] storage list = _locks[user][lockType];
        uint256 len = list.length;
        uint256 i = cursor;

        while (i < len && processed < maxScanLimit) {
            processed++;
            LockRecord storage rec = list[i];
            stats.totalCount += 1;
            stats.totalAmount += rec.amount;

            if (rec.fragmentStatus) {
                stats.fragmentedCount += 1;
                stats.fragmentedAmount += rec.amount;
            } else if (rec.time <= block.timestamp) {
                if (rec.claimStatus) {
                    stats.claimedCount += 1;
                    stats.claimedAmount += rec.amount;
                } else {
                    stats.claimableCount += 1;
                    stats.claimableAmount += rec.amount;
                }
            } else {
                stats.unmaturedCount += 1;
                stats.unmaturedAmount += rec.amount;
            }

            if (!rec.fragmentStatus && rec.time > block.timestamp) {
                if (stats.earliestUnlockTime == 0 || rec.time < stats.earliestUnlockTime) {
                    stats.earliestUnlockTime = rec.time;
                }
                if (rec.time > stats.latestUnlockTime) {
                    stats.latestUnlockTime = rec.time;
                }
            }

            i++;
        }

        nextCursor = i;
        finished = (i >= len);
        if (len == 0) {
            stats.lastIndex = 0;
        } else if (finished) {
            stats.lastIndex = len - 1;
        } else {
            stats.lastIndex = i - 1;
        }
    }

    function getOrder(address user, uint256 orderId) external view returns (OrderRecord memory) {
        _requireStartedView();
        if (!_orderExists[user][orderId]) {
            revert BizError(ORDER_NOT_FOUND);
        }
        return _orders[user][orderId];
    }

    modifier onlyAdmin() {
        if (!_isAdmin(msg.sender)) {
            revert BizError(NOT_ADMIN);
        }
        _;
    }

    function _isAdmin(address who) private view returns (bool) {
        return (who == admin || who == owner);
    }

    function _scaleAmount(uint256 rawAmount) private pure returns (uint256) {
        if (rawAmount == 0) {
            return 0;
        }
        return rawAmount * AMOUNT_SCALE;
    }

    function _allocateOne(
        address to,
        uint256 orderId,
        uint8 lockType,
        uint8 distType,
        uint256 amount
    ) private {
        if (amount == 0) {
            return;
        }
        if (distType == 1) {
            _pushLock(to, lockType, amount);
        } else {
            _mint(to, amount);
        }
        emit AllocateDetail(to, orderId, lockType, distType, amount);
    }

    function _requireStarted() private view {
        if (miningStart == 0) {
            revert BizError(MINING_NOT_STARTED);
        }
    }

    function _requireStartedView() private view {
        if (miningStart == 0) {
            revert BizError(MINING_NOT_STARTED);
        }
    }

    function _requireOrderNotExists(address user, uint256 orderId) private view {
        if (_orderExists[user][orderId]) {
            revert BizError(ORDER_ID_DUPLICATE);
        }
    }

    function _isValidLockType(uint8 lockType) private pure returns (bool) {
        return (lockType == 1 || lockType == 2 || lockType == 3);
    }

    function _currentYear() private view returns (uint256) {
        return ((block.timestamp - miningStart) / YEAR) + 1;
    }

    function _simulateToCurrentYear()
    private
    view
    returns (uint256 budget, uint256 minted, uint256 startTs)
    {
        uint256 tempRemaining = remainingCap;
        uint256 tempBudget = yearBudget;
        uint256 tempMinted = yearMinted;
        uint256 tempLastSettled = lastSettledYear;
        uint256 tempStartTs = yearStartTs;

        uint256 currentYear = _currentYear();
        while (tempLastSettled + 1 < currentYear) {
            uint256 unminted = tempBudget - tempMinted;
            tempRemaining -= unminted;
            tempLastSettled += 1;
            tempBudget = tempRemaining / 2;
            tempMinted = 0;
            tempStartTs = miningStart + (tempLastSettled * YEAR);
        }

        return (tempBudget, tempMinted, tempStartTs);
    }

    function _pushLock(address to, uint8 lockType, uint256 amount) private {
        uint256 unlockTime;
        if (lockType == 1) {
            unlockTime = block.timestamp + MONTH;
        } else if (lockType == 2) {
            unlockTime = block.timestamp + (2 * MONTH);
        } else {
            unlockTime = block.timestamp + (4 * MONTH);
        }

        _locks[to][lockType].push(
            LockRecord({
                time: unlockTime,
                amount: amount,
                claimStatus: false,
                fragmentStatus: false
            })
        );
    }

    function _calcBurn(uint8 lockType, uint256 claimable) private pure returns (uint256) {
        if (lockType == 1) {
            return (claimable * 75) / 100;
        }
        if (lockType == 2) {
            return (claimable * 50) / 100;
        }
        return 0;
    }

    function _transfer(address from, address to, uint256 amount) internal {
        if (to == address(0) || from == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        uint256 bal = _balances[from];
        if (bal < amount) {
            revert BizError(INSUFFICIENT_BALANCE);
        }
        _balances[from] = bal - amount;
        _balances[to] += amount;
        emit Transfer(from, to, amount);
    }

    function _mint(address to, uint256 amount) internal {
        if (to == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        if (_totalSupply + amount > CAP) {
            revert BizError(CAP_EXCEEDED);
        }
        _totalSupply += amount;
        _balances[to] += amount;
        emit Transfer(address(0), to, amount);
    }

    function _burn(address from, uint256 amount) internal {
        if (from == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        uint256 bal = _balances[from];
        if (bal < amount) {
            revert BizError(INSUFFICIENT_BALANCE);
        }
        _balances[from] = bal - amount;
        _totalSupply -= amount;
        emit Transfer(from, address(0), amount);
    }

    function _approve(address owner_, address spender, uint256 amount) internal {
        if (owner_ == address(0) || spender == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        _allowances[owner_][spender] = amount;
        emit Approval(owner_, spender, amount);
    }
}
