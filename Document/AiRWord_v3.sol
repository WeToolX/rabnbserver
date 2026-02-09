// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @title AiRword Aion (AiRWord_v3)
/// @notice 依据 air.md 文档实现的合约（中文注释与中文日志）
contract AiRWord_v3 {
    // =========================
    // 基础常量与元数据
    // =========================
    string public constant NAME = "AiRword Aion";
    string public constant SYMBOL = "AiRword";
    uint8 public constant DECIMALS = 18;
    uint256 public constant CAP = 210_000_000 * 10 ** uint256(DECIMALS);

    // 时间常量
    uint256 private constant MONTH = 1 minutes;
    uint256 private constant YEAR = 1 hours;

    // =========================
    // 错误码（BizError）
    // =========================
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

    // =========================
    // 角色与权限
    // =========================
    address public immutable owner; // 合约部署者（超级管理员）
    address public admin; // 管理员（由部署者设置）
    address public immutable community; // 社区地址

    // =========================
    // ERC20 存储
    // =========================
    // 代币总供应量（最小单位）
    uint256 private _totalSupply;

    // 账户余额映射：地址 => 余额
    mapping(address => uint256) private _balances;

    // 授权额度映射：授权人 => 被授权人 => 授权额度
    mapping(address => mapping(address => uint256)) private _allowances;


    // =========================
    // 挖矿与发行状态
    // =========================
    uint256 public miningStart;     // 挖矿起始时间
    uint256 public lastSettledYear; // 已完成结算的最后一年
    uint256 public yearBudget;      // 当前年度最大发行额度
    uint256 public yearMinted;      // 当前年度已分发额度
    uint256 public remainingCap;    // 剩余可挖额度（全局总量，不是当年额度）
    uint256 public yearStartTs;     // 当前年度起始时间

    // =========================
    // 扫描上限
    // =========================
    uint256 public maxScanLimit = 100;
    uint256 public maxBatchLimit = 100;

    // =========================
    // 数据结构
    // =========================
    struct LockRecord {
        uint256 time;           // 解锁时间戳
        uint256 amount;         // 额度
        bool claimStatus;       // 是否已领取
        bool fragmentStatus;    // 是否已兑换碎片
    }

    // claimAll 计算过程内使用
    struct ClaimAllState {
        uint256 cursorStart; // 起始游标
        uint256 i;           // 当前扫描位置
        uint256 processed;   // 本次处理条数
        uint256 claimable;   // 本次可领取总额
    }

    // 批量分发入参
    struct BatchItem {
        address to;         // 接收用户
        uint8 lockType;     // 仓位（distType=1 时为 1/2/3；distType=2 时必须为 0）
        uint8 distType;     // 分发类型（1=入仓，2=直接分发）
        uint256 amount;     // 分发数量
        uint256 orderId;    // 订单号
    }

    struct LockStats {
        uint256 totalCount;         // 该仓总记录数
        uint256 totalAmount;        // 该仓总额度
        uint256 claimableCount;     // 可领取记录数（已到期且未领取未兑换）
        uint256 claimableAmount;    // 可领取额度
        uint256 unmaturedCount;     // 未到期记录数
        uint256 unmaturedAmount;    // 未到期额度
        uint256 claimedCount;       // 已领取记录数
        uint256 claimedAmount;      // 已领取额度
        uint256 fragmentedCount;    // 已兑换碎片记录数
        uint256 fragmentedAmount;   // 已兑换碎片额度
        uint256 earliestUnlockTime; // 最近一次可解锁时间（用于前端倒计时）
        uint256 latestUnlockTime;   // 最晚解锁时间
        uint256 lastIndex;          // 最后一条记录索引（便于分页）
    }

    struct PreviewClaimable {
        uint256 claimable;   // 本次可领取总额
        uint256 burnAmount;  // 本次应销毁数量
        uint256 netAmount;   // 本次实际到账数量
        uint256 processed;   // 本次处理条数
        uint256 nextCursor;  // 下一游标位置（仅计算，不入库）
    }

    enum OrderMethodType {
        ALLOCATE,         // 分发入仓 / 直接分发
        CLAIM,            // 领取
        EXCHANGE_LOCKED,  // 兑换未解锁碎片
        EXCHANGE_UNLOCKED // 兑换已解锁碎片
    }

    struct OrderRecord {
        OrderMethodType methodType; // 方法类型
        address user;               // 订单归属用户
        uint8 lockType;             // 仓位
        uint256 amount;             // 数量入参（allocate=amount / exchange=targetAmount / claim=0）
        uint256 executedAmount;     // 本次实际执行数量
        uint256 netAmount;          // 实际到账数量（仅领取有意义）
        uint256 burnAmount;         // 本次销毁数量
        uint256 timestamp;          // 执行时间
        uint8 status;               // 执行状态（0=成功，1=失败）
    }

    // =========================
    // 存储映射
    // =========================
    // 用户锁仓：user => lockType => records
    mapping(address => mapping(uint8 => LockRecord[])) private _locks;

    // 游标：user => lockType => mode => cursor
    mapping(address => mapping(uint8 => mapping(uint8 => uint256))) private _cursors;

    // 订单记录：user => orderId => record
    mapping(address => mapping(uint256 => OrderRecord)) private _orders;
    mapping(address => mapping(uint256 => bool)) private _orderExists;

    // 用户授权：user => operator => approved
    mapping(address => mapping(address => bool)) private _operatorApproved;

    // 模式常量（用于游标）
    uint8 private constant MODE_CLAIM = 0;
    uint8 private constant MODE_FRAG_LOCKED = 1;
    uint8 private constant MODE_FRAG_UNLOCKED = 2;

    // =========================
    // 事件（日志输出）
    // =========================
    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    // 年度结算：未挖完额度销毁
    event UnmintedAllocationBurned(uint256 year, uint256 unminted);

    // 领取销毁明细
    event ClaimWithBurn(
        address indexed user,
        uint256 claimable,
        uint256 burnAmount,
        uint256 netAmount,
        uint8 lockType
    );

    // 直接分发事件
    event DirectDistributed(
        address indexed to,
        uint256 amount,
        uint256 orderId,
        uint256 timestamp
    );

    // =========================
    // 构造函数（部署阶段）
    // =========================
    constructor(address admin_, address communityAddress) {
        if (admin_ == address(0) || communityAddress == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }

        owner = msg.sender;
        admin = admin_;
        community = communityAddress;

        // 初始分配（唯一例外可 mint）
        uint256 toContract = (CAP * 4) / 100;
        uint256 toCommunity = (CAP * 6) / 100;
        _mint(address(this), toContract);
        _mint(communityAddress, toCommunity);

        // 初始化剩余可挖额度
        remainingCap = CAP - toContract - toCommunity;
    }

    // =========================
    // ERC20 基础接口
    // =========================
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

    // =========================
    // 权限与管理员
    // =========================
    function setAdmin(address newAdmin) external {
        // 仅部署者可设置管理员
        if (msg.sender != owner) {
            revert BizError(NOT_ADMIN);
        }
        if (newAdmin == address(0)) {
            revert BizError(INVALID_ADDRESS);
        }
        admin = newAdmin;
    }

    function approveOperator(address operator, bool approved) external {
        // 用户授权管理员操作
        _operatorApproved[msg.sender][operator] = approved;
    }

    // 查询授权状态：user 是否授权 operator
    function isOperatorApproved(address user, address operator) external view returns (bool) {
        return _operatorApproved[user][operator];
    }

    // =========================
    // 扫描上限配置
    // =========================
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
        // 预估建议上限：根据区块 gasLimit 反推出可安全处理的最大条数
        // perRecordGas：每条记录预计消耗 gas
        // fixedGas：固定开销（不随记录数变化）
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

    // =========================
    // 启动挖矿
    // =========================
    function startMining() external onlyAdmin {
        // 重复调用忽略
        if (miningStart != 0) {
            return;
        }
        miningStart = block.timestamp;
        yearStartTs = miningStart;
        yearBudget = remainingCap / 2;
        yearMinted = 0;
        lastSettledYear = 0;
    }

    // =========================
    // 年度结算
    // =========================
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

    // =========================
    // 今日可发行量（只读）
    // =========================
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

    // =========================
    // 当前年度剩余额度（只读）
    // =========================
    function getCurrentYearRemaining()
        external
        view
        returns (uint256 yearRemaining, uint256 budget, uint256 minted)
    {
        _requireStartedView();
        (uint256 b, uint256 m, ) = _simulateToCurrentYear();
        return (b - m, b, m);
    }

    // =========================
    // 分发额度（入仓/直接）
    // =========================
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
            // 直接分发：直接 mint 给用户
            _mint(to, amount);
            emit DirectDistributed(to, amount, orderId, block.timestamp);
        }

        // 写入订单记录（ALLOCATE）
        OrderRecord storage r = _orders[to][orderId];
        r.methodType = OrderMethodType.ALLOCATE;
        r.user = to;
        r.lockType = lockType;
        r.amount = amount;
        r.executedAmount = amount;
        r.netAmount = (distType == 2) ? amount : 0;
        r.burnAmount = 0;
        r.timestamp = block.timestamp;
        r.status = 0;
        _orderExists[to][orderId] = true;
    }

    // =========================
    // 批量分发额度（入仓/直接）
    // =========================
    function allocateEmissionToLocksBatch(BatchItem[] calldata items) external onlyAdmin {
        _requireStarted();
        if (items.length == 0) {
            revert BizError(EMPTY_BATCH);
        }
        if (items.length > maxBatchLimit) {
            revert BizError(BATCH_LIMIT_EXCEEDED);
        }

        settleToCurrentYear();

        for (uint256 i = 0; i < items.length; i++) {
            BatchItem calldata it = items[i];

            if (it.to == address(0)) {
                revert BizError(INVALID_ADDRESS);
            }
            if (it.amount == 0) {
                revert BizError(ZERO_AMOUNT);
            }
            if (it.distType != 1 && it.distType != 2) {
                revert BizError(INVALID_DIST_TYPE);
            }
            if (it.distType == 2) {
                if (it.lockType != 0) {
                    revert BizError(INVALID_LOCK_TYPE);
                }
            } else {
                if (!_isValidLockType(it.lockType)) {
                    revert BizError(INVALID_LOCK_TYPE);
                }
            }
            _requireOrderNotExists(it.to, it.orderId);

            if (yearMinted + it.amount > yearBudget) {
                revert BizError(ANNUAL_BUDGET_EXCEEDED);
            }
            yearMinted += it.amount;
            remainingCap -= it.amount;

            if (it.distType == 1) {
                _pushLock(it.to, it.lockType, it.amount);
            } else {
                _mint(it.to, it.amount);
                emit DirectDistributed(it.to, it.amount, it.orderId, block.timestamp);
            }

            OrderRecord storage r = _orders[it.to][it.orderId];
            r.methodType = OrderMethodType.ALLOCATE;
            r.user = it.to;
            r.lockType = it.lockType;
            r.amount = it.amount;
            r.executedAmount = it.amount;
            r.netAmount = (it.distType == 2) ? it.amount : 0;
            r.burnAmount = 0;
            r.timestamp = block.timestamp;
            r.status = 0;
            _orderExists[it.to][it.orderId] = true;
        }
    }

    // =========================
    // 一键领取（指定仓位）
    // =========================
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
                // 未到期直接停止
                break;
            }

            if (rec.claimStatus || rec.fragmentStatus) {
                // 已领取或已兑换，跳过
                s.i++;
                continue;
            }

            // 可领取
            rec.claimStatus = true;
            s.claimable += rec.amount;
            s.i++;
        }

        // 更新游标
        _cursors[user][lockType][MODE_CLAIM] = s.i;

        if (s.claimable == 0) {
            // 若游标未前进，说明本次无法领取
            if (s.i == s.cursorStart) {
                revert BizError(NO_CLAIMABLE);
            }
            // 游标已前进但无可领取（例如前段记录已领取/已兑换）
            OrderRecord storage r0 = _orders[user][orderId];
            r0.methodType = OrderMethodType.CLAIM;
            r0.user = user;
            r0.lockType = lockType;
            r0.amount = 0;
            r0.executedAmount = 0;
            r0.netAmount = 0;
            r0.burnAmount = 0;
            r0.timestamp = block.timestamp;
            r0.status = 0;
            _orderExists[user][orderId] = true;
            return 0;
        }

        uint256 burnAmount = _calcBurn(lockType, s.claimable);
        uint256 netAmount = s.claimable - burnAmount;

        // 领取：先 mint 到自身，再 burn，最后转账净额
        _mint(address(this), s.claimable);
        if (burnAmount > 0) {
            _burn(address(this), burnAmount);
        }
        _transfer(address(this), user, netAmount);

        emit ClaimWithBurn(user, s.claimable, burnAmount, netAmount, lockType);

        // 写入订单记录（CLAIM）
        OrderRecord storage r = _orders[user][orderId];
        r.methodType = OrderMethodType.CLAIM;
        r.user = user;
        r.lockType = lockType;
        r.amount = 0;
        r.executedAmount = s.claimable;
        r.netAmount = netAmount;
        r.burnAmount = burnAmount;
        r.timestamp = block.timestamp;
        r.status = 0;
        _orderExists[user][orderId] = true;

        return netAmount;
    }

    // =========================
    // 兑换未解锁碎片（管理员代用户执行）
    // =========================
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
                // 未到期
                if (rec.fragmentStatus) {
                    i++;
                    continue;
                }
                if (rec.claimStatus) {
                    break;
                }

                // 可兑换未解锁碎片
                rec.fragmentStatus = true;
                sum += rec.amount;
                i++;
                if (sum >= targetAmount) {
                    break;
                }
                continue;
            }

            // 已到期
            if (rec.claimStatus) {
                break;
            }
            if (rec.fragmentStatus) {
                i++;
                continue;
            }

            // 已到期未兑换，未解锁碎片不处理，继续扫描
            i++;
        }

        // 更新游标
        _cursors[user][lockType][MODE_FRAG_LOCKED] = i;

        if (sum < targetAmount) {
            revert BizError(EXCHANGE_TARGET_NOT_MET);
        }

        // 兑换碎片：先 mint 到自身，再全量 burn，不转账
        _mint(address(this), sum);
        _burn(address(this), sum);

        // 写入订单记录（EXCHANGE_LOCKED）
        OrderRecord storage r = _orders[user][orderId];
        r.methodType = OrderMethodType.EXCHANGE_LOCKED;
        r.user = user;
        r.lockType = lockType;
        r.amount = targetAmount;
        r.executedAmount = sum;
        r.netAmount = 0;
        r.burnAmount = sum;
        r.timestamp = block.timestamp;
        r.status = 0;
        _orderExists[user][orderId] = true;

        return sum;
    }

    // =========================
    // 兑换已解锁碎片（管理员代用户执行）
    // =========================
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
                // 未到期直接停止
                break;
            }

            // 已到期
            if (rec.claimStatus) {
                break;
            }
            if (rec.fragmentStatus) {
                break;
            }

            // 可兑换已解锁碎片
            rec.fragmentStatus = true;
            sum += rec.amount;
            i++;
            if (sum >= targetAmount) {
                break;
            }
        }

        // 更新游标
        _cursors[user][lockType][MODE_FRAG_UNLOCKED] = i;

        if (sum < targetAmount) {
            revert BizError(EXCHANGE_TARGET_NOT_MET);
        }

        // 兑换碎片：先 mint 到自身，再全量 burn，不转账
        _mint(address(this), sum);
        _burn(address(this), sum);

        // 写入订单记录（EXCHANGE_UNLOCKED）
        OrderRecord storage r = _orders[user][orderId];
        r.methodType = OrderMethodType.EXCHANGE_UNLOCKED;
        r.user = user;
        r.lockType = lockType;
        r.amount = targetAmount;
        r.executedAmount = sum;
        r.netAmount = 0;
        r.burnAmount = sum;
        r.timestamp = block.timestamp;
        r.status = 0;
        _orderExists[user][orderId] = true;

        return sum;
    }

    // =========================
    // 领取预览（仅 CLAIM）
    // =========================
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

    // =========================
    // 锁仓统计（全量遍历）
    // =========================
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
                // 已兑换碎片
                stats.fragmentedCount += 1;
                stats.fragmentedAmount += rec.amount;
            } else if (rec.time <= block.timestamp) {
                // 已到期
                if (rec.claimStatus) {
                    stats.claimedCount += 1;
                    stats.claimedAmount += rec.amount;
                } else {
                    stats.claimableCount += 1;
                    stats.claimableAmount += rec.amount;
                }
            } else {
                // 未到期且未兑换
                stats.unmaturedCount += 1;
                stats.unmaturedAmount += rec.amount;
            }

            // 最近/最晚解锁时间（仅统计未兑换且未到期）
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

    // =========================
    // 锁仓统计（分页遍历，避免过大数据导致失败）
    // =========================
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

            // 最近/最晚解锁时间（仅统计未兑换且未到期）
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

    // =========================
    // 订单查询
    // =========================
    function getOrder(address user, uint256 orderId) external view returns (OrderRecord memory) {
        _requireStartedView();
        if (!_orderExists[user][orderId]) {
            revert BizError(ORDER_NOT_FOUND);
        }
        return _orders[user][orderId];
    }

    // =========================
    // 内部工具函数
    // =========================
    modifier onlyAdmin() {
        if (!_isAdmin(msg.sender)) {
            revert BizError(NOT_ADMIN);
        }
        _;
    }

    function _isAdmin(address who) private view returns (bool) {
        return (who == admin || who == owner);
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

    // 仅用于 view 的年度模拟结算
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

    // =========================
    // ERC20 内部实现
    // =========================
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
