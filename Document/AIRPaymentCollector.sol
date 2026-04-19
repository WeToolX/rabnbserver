// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @notice AIR 代币最小接口
interface IAIRToken {
    function allowance(address owner_, address spender) external view returns (uint256);
    function balanceOf(address account) external view returns (uint256);
}

/// @title AIRPaymentCollector
/// @notice 用户先对本合约执行 approve，然后由授权收款员代扣 AIR 到收款地址
/// @dev amount 使用 AIR 最小单位（18 位精度）
contract AIRPaymentCollector {
    // =========================
    // 错误码（BizError）
    // =========================
    error BizError(uint8 code);

    uint8 public constant ERR_NOT_OWNER = 1;
    uint8 public constant ERR_NOT_COLLECTOR = 2;
    uint8 public constant ERR_CONTRACT_PAUSED = 3;
    uint8 public constant ERR_INVALID_TREASURY = 4;
    uint8 public constant ERR_INVALID_COLLECTOR = 5;
    uint8 public constant ERR_INVALID_NEW_OWNER = 6;
    uint8 public constant ERR_INVALID_PAYER = 7;
    uint8 public constant ERR_ZERO_AMOUNT = 8;
    uint8 public constant ERR_EMPTY_ORDER_ID = 9;
    uint8 public constant ERR_ORDER_ALREADY_USED = 10;
    uint8 public constant ERR_INSUFFICIENT_ALLOWANCE = 11;
    uint8 public constant ERR_INSUFFICIENT_BALANCE = 12;
    uint8 public constant ERR_TOKEN_TRANSFER_FAILED = 13;
    uint8 public constant ERR_ORDER_NOT_FOUND = 14;

    // =========================
    // 常量
    // =========================
    address public constant AIR_TOKEN = 0xB6f92E39CCCe7E6d49A4DF0E8827C13DE1696e5F;
    bytes4 private constant TRANSFER_FROM_SELECTOR = 0x23b872dd;

    // =========================
    // 状态
    // =========================
    address public owner;
    address public treasury;
    bool public paused;
    uint256 public totalCollected;

    mapping(address => bool) public collectors;
    mapping(bytes32 => bool) public orderUsed;

    struct OrderRecord {
        address payer;
        address recipient;
        address operator;
        uint256 amount;
        uint256 timestamp;
    }

    mapping(bytes32 => OrderRecord) private _orders;

    // =========================
    // 事件
    // =========================
    event OwnershipTransferred(address indexed oldOwner, address indexed newOwner);
    event TreasuryUpdated(address indexed oldTreasury, address indexed newTreasury);
    event CollectorUpdated(address indexed collector, bool enabled);
    event Paused(address indexed operator);
    event Unpaused(address indexed operator);
    event PaymentCollected(
        bytes32 indexed orderId,
        address indexed payer,
        address indexed recipient,
        address operator,
        uint256 amount
    );

    // =========================
    // 构造
    // =========================
    constructor(address treasury_) {
        if (treasury_ == address(0)) {
            revert BizError(ERR_INVALID_TREASURY);
        }

        owner = msg.sender;
        treasury = treasury_;

        emit OwnershipTransferred(address(0), msg.sender);
        emit TreasuryUpdated(address(0), treasury_);
    }

    // =========================
    // 修饰器
    // =========================
    modifier onlyOwner() {
        if (msg.sender != owner) {
            revert BizError(ERR_NOT_OWNER);
        }
        _;
    }

    modifier onlyCollector() {
        if (msg.sender != owner && !collectors[msg.sender]) {
            revert BizError(ERR_NOT_COLLECTOR);
        }
        _;
    }

    modifier whenNotPaused() {
        if (paused) {
            revert BizError(ERR_CONTRACT_PAUSED);
        }
        _;
    }

    // =========================
    // 管理函数
    // =========================
    function transferOwnership(address newOwner) external onlyOwner {
        if (newOwner == address(0)) {
            revert BizError(ERR_INVALID_NEW_OWNER);
        }

        address oldOwner = owner;
        owner = newOwner;

        emit OwnershipTransferred(oldOwner, newOwner);
    }

    function setTreasury(address newTreasury) external onlyOwner {
        if (newTreasury == address(0)) {
            revert BizError(ERR_INVALID_TREASURY);
        }

        address oldTreasury = treasury;
        treasury = newTreasury;

        emit TreasuryUpdated(oldTreasury, newTreasury);
    }

    function setCollector(address collector, bool enabled) external onlyOwner {
        if (collector == address(0)) {
            revert BizError(ERR_INVALID_COLLECTOR);
        }

        collectors[collector] = enabled;
        emit CollectorUpdated(collector, enabled);
    }

    function pause() external onlyOwner {
        paused = true;
        emit Paused(msg.sender);
    }

    function unpause() external onlyOwner {
        paused = false;
        emit Unpaused(msg.sender);
    }

    // =========================
    // 收款主流程
    // =========================
    function collect(bytes32 orderId, address payer, uint256 amount)
        external
        onlyCollector
        whenNotPaused
    {
        _validateCollect(orderId, payer, amount);

        // 先占位，若 transferFrom 失败整笔交易会回滚
        orderUsed[orderId] = true;

        _safeTransferFromAIR(payer, treasury, amount);

        _orders[orderId] = OrderRecord({
            payer: payer,
            recipient: treasury,
            operator: msg.sender,
            amount: amount,
            timestamp: block.timestamp
        });

        totalCollected += amount;

        emit PaymentCollected(orderId, payer, treasury, msg.sender, amount);
    }

    // =========================
    // 只读查询
    // =========================
    function previewCollect(bytes32 orderId, address payer, uint256 amount)
        external
        view
        returns (
            bool executable,
            uint8 code,
            uint256 balance,
            uint256 allowance_,
            address recipient
        )
    {
        recipient = treasury;

        if (paused) {
            return (false, ERR_CONTRACT_PAUSED, 0, 0, recipient);
        }
        if (payer == address(0)) {
            return (false, ERR_INVALID_PAYER, 0, 0, recipient);
        }
        if (amount == 0) {
            return (false, ERR_ZERO_AMOUNT, 0, 0, recipient);
        }
        if (orderId == bytes32(0)) {
            return (false, ERR_EMPTY_ORDER_ID, 0, 0, recipient);
        }
        if (orderUsed[orderId]) {
            return (false, ERR_ORDER_ALREADY_USED, 0, 0, recipient);
        }

        balance = IAIRToken(AIR_TOKEN).balanceOf(payer);
        allowance_ = IAIRToken(AIR_TOKEN).allowance(payer, address(this));

        if (allowance_ < amount) {
            return (false, ERR_INSUFFICIENT_ALLOWANCE, balance, allowance_, recipient);
        }
        if (balance < amount) {
            return (false, ERR_INSUFFICIENT_BALANCE, balance, allowance_, recipient);
        }

        return (true, 0, balance, allowance_, recipient);
    }

    function getOrder(bytes32 orderId)
        external
        view
        returns (
            address payer,
            address recipient,
            address operator,
            uint256 amount,
            uint256 timestamp
        )
    {
        if (!orderUsed[orderId]) {
            revert BizError(ERR_ORDER_NOT_FOUND);
        }

        OrderRecord memory order_ = _orders[orderId];
        return (
            order_.payer,
            order_.recipient,
            order_.operator,
            order_.amount,
            order_.timestamp
        );
    }

    function hashOrderNo(string calldata rawOrderNo) external pure returns (bytes32) {
        return keccak256(bytes(rawOrderNo));
    }

    function explainErrorCode(uint8 code) external pure returns (string memory) {
        if (code == 0) return "OK";
        if (code == ERR_NOT_OWNER) return "caller is not owner";
        if (code == ERR_NOT_COLLECTOR) return "caller is not an authorized collector";
        if (code == ERR_CONTRACT_PAUSED) return "contract is paused";
        if (code == ERR_INVALID_TREASURY) return "treasury address is zero";
        if (code == ERR_INVALID_COLLECTOR) return "collector address is zero";
        if (code == ERR_INVALID_NEW_OWNER) return "new owner address is zero";
        if (code == ERR_INVALID_PAYER) return "payer address is zero";
        if (code == ERR_ZERO_AMOUNT) return "amount is zero";
        if (code == ERR_EMPTY_ORDER_ID) return "orderId is empty";
        if (code == ERR_ORDER_ALREADY_USED) return "orderId already used";
        if (code == ERR_INSUFFICIENT_ALLOWANCE) return "allowance is insufficient";
        if (code == ERR_INSUFFICIENT_BALANCE) return "payer balance is insufficient";
        if (code == ERR_TOKEN_TRANSFER_FAILED) return "AIR transferFrom failed";
        if (code == ERR_ORDER_NOT_FOUND) return "order record not found";
        return "unknown error code";
    }

    // =========================
    // 内部函数
    // =========================
    function _validateCollect(bytes32 orderId, address payer, uint256 amount) private view {
        if (payer == address(0)) {
            revert BizError(ERR_INVALID_PAYER);
        }
        if (amount == 0) {
            revert BizError(ERR_ZERO_AMOUNT);
        }
        if (orderId == bytes32(0)) {
            revert BizError(ERR_EMPTY_ORDER_ID);
        }
        if (orderUsed[orderId]) {
            revert BizError(ERR_ORDER_ALREADY_USED);
        }
        if (IAIRToken(AIR_TOKEN).allowance(payer, address(this)) < amount) {
            revert BizError(ERR_INSUFFICIENT_ALLOWANCE);
        }
        if (IAIRToken(AIR_TOKEN).balanceOf(payer) < amount) {
            revert BizError(ERR_INSUFFICIENT_BALANCE);
        }
    }

    function _safeTransferFromAIR(address from, address to, uint256 amount) private {
        (bool success, bytes memory data) = AIR_TOKEN.call(
            abi.encodeWithSelector(TRANSFER_FROM_SELECTOR, from, to, amount)
        );

        if (!success) {
            revert BizError(ERR_TOKEN_TRANSFER_FAILED);
        }

        if (data.length > 0 && !abi.decode(data, (bool))) {
            revert BizError(ERR_TOKEN_TRANSFER_FAILED);
        }
    }
}
