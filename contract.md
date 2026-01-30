##合约文档
-- 这个区块链合约项目是BNB的链 前期需要写BNB的测试链的ABI交互(同时需要预留正式链的ABI)
-- 主要交易代币是 USDT 在测试链中使用测试的 USDT 在下方文档会写
-- 当前项目的项目代币是 AION 代币
-- 当前项目有NFT卡牌。作用是兑换矿机(兑换逻辑是后端销毁用户的NFT卡牌)
-- 当前项目需要用内部余额 余额的充值使用自己的USDT收款合约进行收款并充值

----

## Test USDT(测试使用的USDT 生产时不适应测试USDT):
-- 合约地址: 0x86B90D81F384f6cd04d614a79437FA1f8eFced64
合约代码:

// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract TestUSDT is ERC20 {
constructor() ERC20("Test USDT", "USDT") {
// 初始给部署者 100 万 USDT（18 位小数）
_mint(msg.sender, 1_000_000 * 10**18);
}

    // 测试水龙头：随时给任何地址 mint
    function faucet(address to, uint256 amount) external {
        _mint(to, amount);
    }

    // 覆盖 decimals，模拟真实 USDT（18 位）
    function decimals() public pure override returns (uint8) {
        return 18;
    }
}
合约ABI:
[{"inputs":[],"stateMutability":"nonpayable","type":"constructor"},{"inputs":[{"internalType":"address","name":"spender","type":"address"},{"internalType":"uint256","name":"allowance","type":"uint256"},{"internalType":"uint256","name":"needed","type":"uint256"}],"name":"ERC20InsufficientAllowance","type":"error"},{"inputs":[{"internalType":"address","name":"sender","type":"address"},{"internalType":"uint256","name":"balance","type":"uint256"},{"internalType":"uint256","name":"needed","type":"uint256"}],"name":"ERC20InsufficientBalance","type":"error"},{"inputs":[{"internalType":"address","name":"approver","type":"address"}],"name":"ERC20InvalidApprover","type":"error"},{"inputs":[{"internalType":"address","name":"receiver","type":"address"}],"name":"ERC20InvalidReceiver","type":"error"},{"inputs":[{"internalType":"address","name":"sender","type":"address"}],"name":"ERC20InvalidSender","type":"error"},{"inputs":[{"internalType":"address","name":"spender","type":"address"}],"name":"ERC20InvalidSpender","type":"error"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"owner","type":"address"},{"indexed":true,"internalType":"address","name":"spender","type":"address"},{"indexed":false,"internalType":"uint256","name":"value","type":"uint256"}],"name":"Approval","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"from","type":"address"},{"indexed":true,"internalType":"address","name":"to","type":"address"},{"indexed":false,"internalType":"uint256","name":"value","type":"uint256"}],"name":"Transfer","type":"event"},{"inputs":[{"internalType":"address","name":"owner","type":"address"},{"internalType":"address","name":"spender","type":"address"}],"name":"allowance","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"spender","type":"address"},{"internalType":"uint256","name":"value","type":"uint256"}],"name":"approve","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"account","type":"address"}],"name":"balanceOf","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"decimals","outputs":[{"internalType":"uint8","name":"","type":"uint8"}],"stateMutability":"pure","type":"function"},{"inputs":[{"internalType":"address","name":"to","type":"address"},{"internalType":"uint256","name":"amount","type":"uint256"}],"name":"faucet","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"name","outputs":[{"internalType":"string","name":"","type":"string"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"symbol","outputs":[{"internalType":"string","name":"","type":"string"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"totalSupply","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"to","type":"address"},{"internalType":"uint256","name":"value","type":"uint256"}],"name":"transfer","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"from","type":"address"},{"internalType":"address","name":"to","type":"address"},{"internalType":"uint256","name":"value","type":"uint256"}],"name":"transferFrom","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"nonpayable","type":"function"}]

## 支付合约
-- PaymentUSDT 合约地址: 0x600F50058c8cEb5Ad9448c82eA3135d4C5539b12
合约代码:
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/security/Pausable.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";

/**
* @title PaymentUSDT
* @notice USDT payment contract on BSC
*
* Design:
*  - Users must approve this contract first
*  - The executor (backend or multisig) calls deposit to pull USDT
*  - Funds are transferred directly to the treasury
*  - Admin (recommended multisig) can update treasury / executor and pause
*  - Owner (deployer) and admin can update the minimum payment amount
*
* Features:
*  - Pull-based payment (no direct user transfer)
*  - OrderId replay protection
*  - Minimum payment enforcement
*  - Emergency pause support
     */
     contract PaymentUSDT is Pausable, ReentrancyGuard {
     using SafeERC20 for IERC20;

/* ========== Immutable Parameters ========== */

/// @notice USDT token contract address
IERC20 public immutable USDT;

/// @notice Contract owner (deployer, fallback governance)
address public immutable owner;

/* ========== Configurable Parameters ========== */

/// @notice Minimum payment amount (USDT smallest unit, 18 decimals)
uint256 public minAmount;

/// @notice Admin address (recommended multisig)
address public admin;

/// @notice Executor address (backend or multisig Safe)
address public executor;

/// @notice Treasury address (cold wallet or multisig)
address public treasury;

/* ========== Order Replay Protection ========== */

/// @notice Records whether an orderId has been executed
mapping(bytes32 => bool) public executed;

/* ========== Events ========== */

/// @notice Emitted when a payment is successfully executed
event Deposit(
bytes32 indexed orderId,
address indexed user,
uint256 amount,
address indexed treasury
);

/// @notice Emitted when treasury address is updated
event TreasuryUpdated(address indexed oldTreasury, address indexed newTreasury);

/// @notice Emitted when executor address is updated
event ExecutorUpdated(address indexed oldExecutor, address indexed newExecutor);

/// @notice Emitted when admin address is updated
event AdminUpdated(address indexed oldAdmin, address indexed newAdmin);

/// @notice Emitted when minimum amount is updated
event MinAmountUpdated(uint256 oldAmount, uint256 newAmount, address indexed caller);

/* ========== Constructor ========== */

constructor(
address usdt_,
address treasury_,
address executor_,
address admin_
) {
require(usdt_ != address(0), "USDT=0");
require(treasury_ != address(0), "treasury=0");
require(executor_ != address(0), "executor=0");
require(admin_ != address(0), "admin=0");

    USDT = IERC20(usdt_);
    treasury = treasury_;
    executor = executor_;
    admin = admin_;

    owner = msg.sender;        // Contract deployer
minAmount = 1_000_000_000_000_000_000;     // Default: 1 USDT (18 decimals)
}

/* ========== Modifiers ========== */

/// @notice Restricts function access to admin only
modifier onlyAdmin() {
require(msg.sender == admin, "not admin");
_;
}

/// @notice Restricts function access to executor only
modifier onlyExecutor() {
require(msg.sender == executor, "not executor");
_;
}

/// @notice Restricts function access to owner or admin
modifier onlyOwnerOrAdmin() {
require(
msg.sender == owner || msg.sender == admin,
"not owner/admin"
);
_;
}

/* ========== Admin Functions ========== */

/// @notice Update treasury address
function setTreasury(address newTreasury) external onlyAdmin {
require(newTreasury != address(0), "treasury=0");
address old = treasury;
treasury = newTreasury;
emit TreasuryUpdated(old, newTreasury);
}

/// @notice Update executor address
function setExecutor(address newExecutor) external onlyAdmin {
require(newExecutor != address(0), "executor=0");
address old = executor;
executor = newExecutor;
emit ExecutorUpdated(old, newExecutor);
}

/// @notice Update admin address
function setAdmin(address newAdmin) external onlyAdmin {
require(newAdmin != address(0), "admin=0");
address old = admin;
admin = newAdmin;
emit AdminUpdated(old, newAdmin);
}

/// @notice Update minimum payment amount (owner or admin)
function setMinAmount(uint256 newAmount) external onlyOwnerOrAdmin {
require(newAmount >= 1_000_000_000_000_000_000, "min < 1 USDT");
uint256 old = minAmount;
minAmount = newAmount;
emit MinAmountUpdated(old, newAmount, msg.sender);
}

/// @notice Pause the contract (emergency)
function pause() external onlyAdmin {
_pause();
}

/// @notice Unpause the contract
function unpause() external onlyAdmin {
_unpause();
}

/* ========== Core Payment Logic ========== */

/**
    * @notice Execute a USDT payment
    * @param orderId Unique order identifier (anti-replay)
    * @param user Address to pull USDT from
* @param amount USDT amount (18 decimals)
      */
      function deposit(
      bytes32 orderId,
      address user,
      uint256 amount
      )
      external
      whenNotPaused
      nonReentrant
      onlyExecutor
      {
      require(!executed[orderId], "order executed");
      require(user != address(0), "user=0");
      require(amount >= minAmount, "amount < min");

      executed[orderId] = true;

      USDT.safeTransferFrom(user, treasury, amount);

      emit Deposit(orderId, user, amount, treasury);
      }
      }
ABI:
[{"inputs":[{"internalType":"address","name":"usdt_","type":"address"},{"internalType":"address","name":"treasury_","type":"address"},{"internalType":"address","name":"executor_","type":"address"},{"internalType":"address","name":"admin_","type":"address"}],"stateMutability":"nonpayable","type":"constructor"},{"inputs":[{"internalType":"address","name":"token","type":"address"}],"name":"SafeERC20FailedOperation","type":"error"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"oldAdmin","type":"address"},{"indexed":true,"internalType":"address","name":"newAdmin","type":"address"}],"name":"AdminUpdated","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"bytes32","name":"orderId","type":"bytes32"},{"indexed":true,"internalType":"address","name":"user","type":"address"},{"indexed":false,"internalType":"uint256","name":"amount","type":"uint256"},{"indexed":true,"internalType":"address","name":"treasury","type":"address"}],"name":"Deposit","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"oldExecutor","type":"address"},{"indexed":true,"internalType":"address","name":"newExecutor","type":"address"}],"name":"ExecutorUpdated","type":"event"},{"anonymous":false,"inputs":[{"indexed":false,"internalType":"uint256","name":"oldAmount","type":"uint256"},{"indexed":false,"internalType":"uint256","name":"newAmount","type":"uint256"},{"indexed":true,"internalType":"address","name":"caller","type":"address"}],"name":"MinAmountUpdated","type":"event"},{"anonymous":false,"inputs":[{"indexed":false,"internalType":"address","name":"account","type":"address"}],"name":"Paused","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"oldTreasury","type":"address"},{"indexed":true,"internalType":"address","name":"newTreasury","type":"address"}],"name":"TreasuryUpdated","type":"event"},{"anonymous":false,"inputs":[{"indexed":false,"internalType":"address","name":"account","type":"address"}],"name":"Unpaused","type":"event"},{"inputs":[],"name":"USDT","outputs":[{"internalType":"contract IERC20","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"admin","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"bytes32","name":"orderId","type":"bytes32"},{"internalType":"address","name":"user","type":"address"},{"internalType":"uint256","name":"amount","type":"uint256"}],"name":"deposit","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"bytes32","name":"","type":"bytes32"}],"name":"executed","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"executor","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"minAmount","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"owner","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"pause","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"paused","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"newAdmin","type":"address"}],"name":"setAdmin","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"newExecutor","type":"address"}],"name":"setExecutor","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"uint256","name":"newAmount","type":"uint256"}],"name":"setMinAmount","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"newTreasury","type":"address"}],"name":"setTreasury","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"treasury","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"unpause","outputs":[],"stateMutability":"nonpayable","type":"function"}]

## NFT卡牌合约
--合约地址:0xaBbDDC8ac523325e6Cd53dE1BaBF0C63B729010a

### 指标定义（最新）
| 指标 | 含义 |
| --- | --- |
| totalMinted | 历史已分发数量（永久） |
| totalSupply | 当前仍存在数量 |
| burnedAmount(addr) | 该地址历史销毁 |
| remainingMintable() | 剩余未分发 |
| MAX_SUPPLY | 历史硬上限 |

> 说明：当前仅同步 ABI 指标名称。如需同步最新合约代码，请提供最新合约源码或完整 ABI。
合约代码:
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
import "@openzeppelin/contracts/token/ERC1155/extensions/ERC1155Supply.sol";
import "@openzeppelin/contracts/token/ERC1155/extensions/ERC1155Burnable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

/**
* 单卡牌 ERC1155（授权销毁 + 已销毁数量统计）
* - tokenId 固定为 1
* - 总量上限 10000
* - 管理员可分发
* - 用户可授权管理员销毁
* - 记录每个地址累计销毁数量
    */
    contract SingleCard1155 is ERC1155, ERC1155Supply, ERC1155Burnable, AccessControl {
    uint256 public constant CARD_ID = 1;
    uint256 public constant MAX_SUPPLY = 10_000;

bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

string public name;
string public symbol;

/// @notice 每个地址历史累计销毁的 CARD_ID 数量
mapping(address => uint256) public burnedAmount;

constructor(
string memory uri_,
string memory name_,
string memory symbol_,
address initialAdmin
) ERC1155(uri_) {
name = name_;
symbol = symbol_;

     _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);

     if (initialAdmin != address(0)) {
         _grantRole(ADMIN_ROLE, initialAdmin);
     }
}

/* ================= 管理员 ================= */

function setAdmin(address admin, bool enabled)
external
onlyRole(DEFAULT_ADMIN_ROLE)
{
if (enabled) _grantRole(ADMIN_ROLE, admin);
else _revokeRole(ADMIN_ROLE, admin);
}

function setURI(string memory newuri)
external
onlyRole(DEFAULT_ADMIN_ROLE)
{
_setURI(newuri);
}

/* ================= 分发 / 铸造 ================= */

function distribute(address to, uint256 amount)
external
onlyRole(ADMIN_ROLE)
{
require(
totalSupply(CARD_ID) + amount <= MAX_SUPPLY,
"exceed max supply"
);
_mint(to, CARD_ID, amount, "");
}

/* ================= OZ v5 关键 override ================= */

function _update(
address from,
address to,
uint256[] memory ids,
uint256[] memory values
) internal override(ERC1155, ERC1155Supply) {
super._update(from, to, ids, values);

     // 统计销毁数量（只统计 CARD_ID）
     if (to == address(0) && from != address(0)) {
         for (uint256 i = 0; i < ids.length; i++) {
             if (ids[i] == CARD_ID) {
                 burnedAmount[from] += values[i];
             }
         }
     }
}

function supportsInterface(bytes4 interfaceId)
public
view
override(ERC1155, AccessControl)
returns (bool)
{
return super.supportsInterface(interfaceId);
}
}


合约ABI:[
{
"type": "constructor",
"inputs": [
{ "internalType": "string", "name": "uri_", "type": "string" },
{ "internalType": "string", "name": "name_", "type": "string" },
{ "internalType": "string", "name": "symbol_", "type": "string" },
{ "internalType": "address", "name": "initialAdmin", "type": "address" }
],
"stateMutability": "nonpayable"
},

/* ========= 常量 ========= */

{
"type": "function",
"name": "CARD_ID",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "uint256" }]
},
{
"type": "function",
"name": "MAX_SUPPLY",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "uint256" }]
},

/* ========= NFT 基本信息 ========= */

{
"type": "function",
"name": "name",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "string" }]
},
{
"type": "function",
"name": "symbol",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "string" }]
},

/* ========= ERC1155 ========= */

{
"type": "function",
"name": "balanceOf",
"stateMutability": "view",
"inputs": [
{ "type": "address", "name": "account" },
{ "type": "uint256", "name": "id" }
],
"outputs": [{ "type": "uint256" }]
},
{
"type": "function",
"name": "balanceOfBatch",
"stateMutability": "view",
"inputs": [
{ "type": "address[]", "name": "accounts" },
{ "type": "uint256[]", "name": "ids" }
],
"outputs": [{ "type": "uint256[]" }]
},
{
"type": "function",
"name": "safeTransferFrom",
"stateMutability": "nonpayable",
"inputs": [
{ "type": "address", "name": "from" },
{ "type": "address", "name": "to" },
{ "type": "uint256", "name": "id" },
{ "type": "uint256", "name": "amount" },
{ "type": "bytes", "name": "data" }
],
"outputs": []
},
{
"type": "function",
"name": "setApprovalForAll",
"stateMutability": "nonpayable",
"inputs": [
{ "type": "address", "name": "operator" },
{ "type": "bool", "name": "approved" }
],
"outputs": []
},
{
"type": "function",
"name": "isApprovedForAll",
"stateMutability": "view",
"inputs": [
{ "type": "address", "name": "account" },
{ "type": "address", "name": "operator" }
],
"outputs": [{ "type": "bool" }]
},

/* ========= Burnable ========= */

{
"type": "function",
"name": "burn",
"stateMutability": "nonpayable",
"inputs": [
{ "type": "address", "name": "from" },
{ "type": "uint256", "name": "id" },
{ "type": "uint256", "name": "amount" }
],
"outputs": []
},

/* ========= Supply ========= */

{
"type": "function",
"name": "totalMinted",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "uint256" }]
},
{
"type": "function",
"name": "totalSupply",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "uint256" }]
},
{
"type": "function",
"name": "remainingMintable",
"stateMutability": "view",
"inputs": [],
"outputs": [{ "type": "uint256" }]
},

/* ========= 自定义业务 ========= */

{
"type": "function",
"name": "distribute",
"stateMutability": "nonpayable",
"inputs": [
{ "type": "address", "name": "to" },
{ "type": "uint256", "name": "amount" }
],
"outputs": []
},
{
"type": "function",
"name": "burnedAmount",
"stateMutability": "view",
"inputs": [{ "type": "address", "name": "account" }],
"outputs": [{ "type": "uint256" }]
},

/* ========= URI ========= */

{
"type": "function",
"name": "uri",
"stateMutability": "view",
"inputs": [{ "type": "uint256", "name": "id" }],
"outputs": [{ "type": "string" }]
},
{
"type": "function",
"name": "setURI",
"stateMutability": "nonpayable",
"inputs": [{ "type": "string", "name": "newuri" }],
"outputs": []
},

/* ========= AccessControl ========= */

{
"type": "function",
"name": "setAdmin",
"stateMutability": "nonpayable",
"inputs": [
{ "type": "address", "name": "admin" },
{ "type": "bool", "name": "enabled" }
],
"outputs": []
},
{
"type": "function",
"name": "hasRole",
"stateMutability": "view",
"inputs": [
{ "type": "bytes32", "name": "role" },
{ "type": "address", "name": "account" }
],
"outputs": [{ "type": "bool" }]
},
{
"type": "function",
"name": "getRoleAdmin",
"stateMutability": "view",
"inputs": [{ "type": "bytes32", "name": "role" }],
"outputs": [{ "type": "bytes32" }]
},

/* ========= supports ========= */

{
"type": "function",
"name": "supportsInterface",
"stateMutability": "view",
"inputs": [{ "type": "bytes4", "name": "interfaceId" }],
"outputs": [{ "type": "bool" }]
},

/* ========= Events ========= */

{
"type": "event",
"name": "TransferSingle",
"anonymous": false,
"inputs": [
{ "indexed": true, "type": "address", "name": "operator" },
{ "indexed": true, "type": "address", "name": "from" },
{ "indexed": true, "type": "address", "name": "to" },
{ "indexed": false, "type": "uint256", "name": "id" },
{ "indexed": false, "type": "uint256", "name": "value" }
]
},
{
"type": "event",
"name": "ApprovalForAll",
"anonymous": false,
"inputs": [
{ "indexed": true, "type": "address", "name": "account" },
{ "indexed": true, "type": "address", "name": "operator" },
{ "indexed": false, "type": "bool", "name": "approved" }
]
}
]


## AiRword Aion (AION) (项目代币) 合约:
合约地址:0x4D49D3118450D8aA32d046fFb0dE163De220BBDc
合约代码:
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/*
* =========================================================
* AiRwordAionLock (AION)
* ---------------------------------------------------------
* - ERC20 标准代币
* - 固定最大总量：2.1 亿 AION
* - 管理员规则化分发（分阶段锁仓 + 即时销毁）
* - 锁仓到期用户自主领取
* - 用户支付/兑换：80% 销毁 + 20% 社区回流
* - 支持暂停、权限隔离、防重入
*
* 网络：BNB Chain / BSC Testnet（EVM 兼容）
* =========================================================
  */

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/security/Pausable.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";

contract AiRwordAionLock is
ERC20,
ERC20Burnable,
AccessControl,
Ownable,
Pausable,
ReentrancyGuard
{
/* =====================================================
* 一、角色与基础参数
* ===================================================== */

    /// 运营管理员角色（仅允许规则化分发）
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    /// 最大总供应量：2.1 亿 AION
    uint256 public constant CAP = 210_000_000 * 1e18;

    /// 百分比基数（10000 = 100%）
    uint256 private constant BPS_DENOM = 10_000;

    /// 初始铸造比例（部署时一次性）
    uint256 private constant INIT_TO_CONTRACT_BPS = 1000; // 10% 给合约自身
    uint256 private constant INIT_TO_MARKET_BPS   = 600;  // 6% 市场地址
    uint256 private constant INIT_TO_ECO_BPS      = 400;  // 4% 生态地址

    /* =====================================================
     * 二、锁仓结构设计
     * ===================================================== */

    /// 锁仓方案（管理员只能三选一）
    enum LockPlan {
        ONE_MONTH,    // 1 个月：25% 锁仓，75% 立即销毁
        TWO_MONTHS,   // 2 个月：50% 锁仓，50% 立即销毁
        FOUR_MONTHS   // 4 个月：100% 锁仓，不销毁
    }

    /// 单条锁仓记录
    struct LockRecord {
        uint128 amount;     // 锁仓数量
        uint64  unlockTime; // 解锁时间戳
        bool    claimed;    // 是否已领取
    }

    /// 用户地址 => 多条锁仓记录
    mapping(address => LockRecord[]) private _locks;

    /* =====================================================
     * 三、事件定义
     * ===================================================== */

    /// 管理员分发事件
    event FaucetMint(
        address indexed to,
        uint256 totalAmount,
        uint256 lockedAmount,
        uint256 burnedAmount,
        uint256 unlockTime,
        LockPlan plan
    );

    /// 用户领取锁仓事件
    event ClaimLocks(address indexed user, uint256 claimedAmount);

    /// 用户支付 / 兑换事件
    event ExchangePaid(
        address indexed user,
        uint256 paidAmount,
        uint256 burnedAmount,
        uint256 toCommunityAmount
    );

    /* =====================================================
     * 四、支付 / 兑换参数
     * ===================================================== */

    /// 社区回流地址
    address public community;

    /// 是否启用固定支付额度
    bool public fixedPriceEnabled = true;

    /// 固定支付数量（默认 100 AION）
    uint256 public fixedPriceAmount = 100 * 1e18;

    /// 支付销毁比例（默认 80%）
    uint256 public burnBps = 8000;

    /// 社区回流比例（默认 20%）
    uint256 public communityBps = 2000;

    /* =====================================================
     * 五、构造函数（部署即执行）
     * ===================================================== */

    constructor(
        address owner_,
        address admin_,
        address market_,
        address eco_,
        address community_
    ) ERC20("AiRword Aion", "AION") Ownable(owner_) {
        require(owner_ != address(0), "owner=0");
        require(admin_ != address(0), "admin=0");
        require(market_ != address(0), "market=0");
        require(eco_ != address(0), "eco=0");
        require(community_ != address(0), "community=0");

        community = community_;

        /// 权限初始化
        _grantRole(DEFAULT_ADMIN_ROLE, owner_);
        _grantRole(ADMIN_ROLE, admin_);

        /// 初始只铸造 20%
        uint256 toContract = (CAP * INIT_TO_CONTRACT_BPS) / BPS_DENOM;
        uint256 toMarket   = (CAP * INIT_TO_MARKET_BPS) / BPS_DENOM;
        uint256 toEco      = (CAP * INIT_TO_ECO_BPS) / BPS_DENOM;

        _mintWithCapCheck(address(this), toContract);
        _mintWithCapCheck(market_, toMarket);
        _mintWithCapCheck(eco_, toEco);
        // 剩余 80% 不铸造，由管理员后续规则化分发
    }

    /* =====================================================
     * 六、内部工具函数
     * ===================================================== */

    /// 带上限校验的铸造
    function _mintWithCapCheck(address to, uint256 amount) internal {
        require(totalSupply() + amount <= CAP, "CAP exceeded");
        _mint(to, amount);
    }

    /// 根据锁仓方案返回锁仓比例和周期
    function _planParams(LockPlan plan)
        internal
        pure
        returns (uint256 lockBps, uint256 durationSeconds)
    {
        if (plan == LockPlan.ONE_MONTH) {
            return (2500, 30 days);
        } else if (plan == LockPlan.TWO_MONTHS) {
            return (5000, 60 days);
        } else {
            return (10_000, 120 days);
        }
    }

    /* =====================================================
     * 七、Owner 管理权限
     * ===================================================== */

    function setAdmin(address newAdmin) external onlyOwner {
        require(newAdmin != address(0), "admin=0");
        _grantRole(ADMIN_ROLE, newAdmin);
    }

    function revokeAdmin(address admin) external onlyOwner {
        revokeRole(ADMIN_ROLE, admin);
    }

    function pause() external onlyOwner { _pause(); }
    function unpause() external onlyOwner { _unpause(); }

    function setCommunity(address newCommunity) external onlyOwner {
        require(newCommunity != address(0), "community=0");
        community = newCommunity;
    }

    function setExchangeParams(
        bool fixedEnabled,
        uint256 fixedAmount,
        uint256 burnBps_,
        uint256 communityBps_
    ) external onlyOwner {
        require(burnBps_ + communityBps_ == BPS_DENOM, "bps error");
        fixedPriceEnabled = fixedEnabled;
        fixedPriceAmount = fixedAmount;
        burnBps = burnBps_;
        communityBps = communityBps_;
    }

    /* =====================================================
     * 八、管理员规则化分发（核心）
     * ===================================================== */

    function faucetMint(
        address to,
        uint256 amount,
        LockPlan plan
    )
        external
        onlyRole(ADMIN_ROLE)
        whenNotPaused
        nonReentrant
    {
        require(to != address(0), "to=0");
        require(amount > 0, "amount=0");

        (uint256 lockBps, uint256 duration) = _planParams(plan);

        uint256 lockedAmount = (amount * lockBps) / BPS_DENOM;
        uint256 burnedAmount = amount - lockedAmount;

        /// 锁仓部分：进入合约并记录
        if (lockedAmount > 0) {
            _mintWithCapCheck(address(this), lockedAmount);
            uint256 unlockTime = block.timestamp + duration;

            _locks[to].push(
                LockRecord({
                    amount: uint128(lockedAmount),
                    unlockTime: uint64(unlockTime),
                    claimed: false
                })
            );

            emit FaucetMint(
                to,
                amount,
                lockedAmount,
                burnedAmount,
                unlockTime,
                plan
            );
        }

        /// 销毁部分：铸造后立即销毁
        if (burnedAmount > 0) {
            _mintWithCapCheck(address(this), burnedAmount);
            _burn(address(this), burnedAmount);
        }
    }

    /* =====================================================
     * 九、锁仓查询与领取
     * ===================================================== */

    function locksOf(address user)
        external
        view
        returns (LockRecord[] memory)
    {
        return _locks[user];
    }

    function claimableAmount(address user)
        public
        view
        returns (uint256 sum)
    {
        LockRecord[] storage arr = _locks[user];
        for (uint256 i = 0; i < arr.length; i++) {
            if (!arr[i].claimed && arr[i].unlockTime <= block.timestamp) {
                sum += arr[i].amount;
            }
        }
    }

    /// 一键领取所有已到期锁仓
    function claimMatured()
        external
        whenNotPaused
        nonReentrant
    {
        LockRecord[] storage arr = _locks[msg.sender];
        uint256 sum = 0;

        for (uint256 i = 0; i < arr.length; i++) {
            if (!arr[i].claimed && arr[i].unlockTime <= block.timestamp) {
                arr[i].claimed = true;
                sum += arr[i].amount;
            }
        }

        require(sum > 0, "nothing to claim");
        _transfer(address(this), msg.sender, sum);

        emit ClaimLocks(msg.sender, sum);
    }

    /* =====================================================
     * 十、用户支付 / 兑换逻辑
     * ===================================================== */

    function payFixed()
        external
        whenNotPaused
        nonReentrant
    {
        require(fixedPriceEnabled, "fixed disabled");
        _payInternal(fixedPriceAmount);
    }

    function payAmount(uint256 amount)
        external
        whenNotPaused
        nonReentrant
    {
        require(!fixedPriceEnabled, "fixed enabled");
        require(amount > 0, "amount=0");
        _payInternal(amount);
    }

    function _payInternal(uint256 amount) internal {
        _transfer(msg.sender, address(this), amount);

        uint256 burnAmount = (amount * burnBps) / BPS_DENOM;
        uint256 toCommunity = amount - burnAmount;

        if (burnAmount > 0) {
            _burn(address(this), burnAmount);
        }
        if (toCommunity > 0) {
            _transfer(address(this), community, toCommunity);
        }

        emit ExchangePaid(msg.sender, amount, burnAmount, toCommunity);
    }

    /* =====================================================
     * 十一、ERC20 钩子（暂停保护）
     * ===================================================== */

    function _update(
        address from,
        address to,
        uint256 value
    )
        internal
        override
        whenNotPaused
    {
        super._update(from, to, value);
    }
}
合约ABI:
  [{"inputs":[{"internalType":"address","name":"owner_","type":"address"},{"internalType":"address","name":"admin_","type":"address"},{"internalType":"address","name":"market_","type":"address"},{"internalType":"address","name":"eco_","type":"address"},{"internalType":"address","name":"community_","type":"address"}],"stateMutability":"nonpayable","type":"constructor"},{"inputs":[],"name": "AccessControlBadConfirmation","type":"error"},{"inputs":[{"internalType":"address","name":"account","type":"address"},{"internalType":"bytes32","name":"neededRole","type":"bytes32"}],"name":"AccessControlUnauthorizedAccount","type":"error"},{"inputs":[{"internalType":"address","name":"spender","type":"address"},{"internalType":"uint256","name":"allowance","type":"uint256"},{"internalTyp e":"uint256","name":"needed","type":"uint256"}],"name":"ERC20InsufficientAllowance","type":"error"},{"inputs":[{"internalType":"address","name":"sender","type":"address"},{"internalType":"uint256","name":"balance","type":"uint256"},{"internalType":"uint256","name":"needed","type":"uint256"}],"name":"ERC20InsufficientBalance","type":"error"},{"inputs":[{"internalType":"address","name":"a pprover","type":"address"}],"name":"ERC20InvalidApprover","type":"error"},{"inputs":[{"internalType":"address","name":"receiver","type":"address"}],"name":"ERC20InvalidReceiver","type":"error"},{"inputs":[{"internalType":"address","name":"sender","type":"address"}],"name":"ERC20InvalidSender","type":"error"},{"inputs":[{"internalType":"address","name":"spender","type":"address"}],"name":"ERC20InvalidSpender","type":"error"},{"inputs":[{"internalType":"address","name":"owner","type":"address"}],"name":"OwnableInvalidOwner","type":"error"},{"inputs":[{"internalType":"address","name":"account","type":"address"}],"name":"OwnableUnauthorizedAccount","type":"error"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"owner","type":"address"},{"indexed":true e,"internalType":"address","name":"spender","type":"address"},{"indexed":false,"internalType":"uint256","name":"value","type":"uint256"}],"name":"Approval","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"user","type":"address"},{"indexed":false,"internalType":"uint256","name":"claimedAmount","type":"uint256"}],"name":"ClaimLocks","type":"event"},{"a nonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"user","type":"address"},{"indexed":false,"internalType":"uint256","name":"paidAmount","type":"uint256"},{"indexed":false,"internalType":"uint256","name":"burnedAmount","type":"uint256"},{"indexed":false,"internalType":"uint256","name":"toCommunityAmount","type":"uint256"}],"name":"ExchangePaid","type":"event"},{"anonymous s":false,"inputs":[{"indexed":true,"internalType":"address","name":"to","type":"address"},{"indexed":false,"internalType":"uint256","name":"totalAmount","type":"uint256"},{"indexed":false,"internalType":"uint256","name":"lockedAmount","type":"uint256"},{"indexed":false,"internalType":"uint256","name":"burnedAmount","type":"uint256"},{"indexed":false,"internalType":"uint256","name":"unlockTime","type":"uint256"},{"indexed":false,"internalType":"enum AiRwordAionLock.LockPlan","name":"plan","type":"uint8"}],"name":"FaucetMint","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"previousOwner","type":"address"},{"indexed":true,"internalType":"address","name":"newOwner","type":"address"}],"name":"OwnershipTransferred","type":"event"},{"anonymous":false,"inputs":[{"indexed":false,"inter nalType":"address","name":"account","type":"address"}],"name":"Paused","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"bytes32","name":"role","type":"bytes32"},{"indexed":true,"internalType":"bytes32","name":"previousAdminRole","type":"bytes32"},{"indexed":true,"internalType":"bytes32","name":"newAdminRole","type":"bytes32"}],"name":"RoleAdminChanged ","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"bytes32","name":"role","type":"bytes32"},{"indexed":true,"internalType":"address","name":"account","type":"address"},{"indexed":true,"internalType":"address","name":"sender","type":"address"}],"name":"RoleGranted","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"bytes32","n名称："角色"，类型："bytes32"},{"已索引：true，内部类型："地址"，名称："帐户"，类型："地址"},{"已索引：true，内部类型："地址"，名称："发件人"，类型："地址"}]，名称："角色已撤销"，类型："事件"},{"匿名：false，输入：[{"已索引：true，内部类型："地址"，名称："发件人"，类型："地址"},{"已索引：true，内部类型："地址"，名称："收件人"，类型：""address"},{"indexed":false,"internalType":"uint256","name":"value","type":"uint256"}],"name":"Transfer","type":"event"},{"anonymous":false,"inputs":[{"indexed":false,"internalType":"address","name":"account","type":"address"}],"name":"Unpaused","type":"event"},{"inputs":[],"name":"ADMIN_ROLE","outputs":[{"internalType":"bytes32","name":"","type":"bytes32"}],"stateMutability ":"view","type":"function"},{"inputs":[],"name":"CAP","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"DEFAULT_ADMIN_ROLE","outputs":[{"internalType":"bytes32","name":"","type":"bytes32"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"owner","type":"ad dress"},{"internalType":"address","name":"spender","type":"address"}],"name":"allowance","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"spender","type":"address"},{"internalType":"uint256","name":"value","type":"uint256"}],"name":"approve","outputs":[{"internalType":"b ool","name":"","type":"bool"}],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"account","type":"address"}],"name":"balanceOf","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"uint256","name":"value","type":"uint256"}],"name":"burn","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"burnBps","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"account","type":"address"},{"internalType":"uint256","name":"value","type":"uint256 "}],"name":"burnFrom","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"claimMatured","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"user","type":"address"}],"name":"claimableAmount","outputs":[{"internalType":"uint256","name" :"sum","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"community","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"communityBps","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMu tability":"view","type":"function"},{"inputs":[],"name":"decimals","outputs":[{"internalType":"uint8","name":"","type":"uint8"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"address","name":"to","type":"address"},{"internalType":"uint256","name":"amount","type":"uint256"},{"internalType":"enum AiRwordAionLock.LockPlan","name":"plan","type":"uint8"}],"name":"faucetMint","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"fixedPriceAmount","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"fixedPriceEnabled","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"bytes32","name":"role","type":"bytes32"}],"name":"getRoleAdmin","outputs":[{"internalType ":"bytes32","name":"","type":"bytes32"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"bytes32","name":"role","type":"bytes32"},{"internalType":"address","name":"account","type":"address"}],"name":"grantRole","outputs":[],"stateMutability":"nonpayable","type":"f函数"},{"输入":[{"内部类型":"bytes32","名称":"角色","类型":"bytes32"},{"内部类型":"地址","名称":"帐户","类型":"地址"}],"名称":"拥有角色","输出":[{"内部类型":"布尔","名称":"","类型":"布尔"}],"状态可变性":"视图","类型":"函数"},{"输入":[{"内部类型":"address","name":"user","type":"address"}],"name":"locksOf","outputs":[{"components":[{"internalType":"uint128","name":"amount","type":"uint128"},{"internalType":"uint64","name":"unlockTime","type":"uint64"},{"internalType":"bool","name":"claimed","type":"bool"}],"internalType":"struct AiRwordAionLock.LockRecord[]","name":"","type":"tuple[]"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"name","outputs":[{"internalType":"string","name":"","type":"string"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"owner","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"pause","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"paused","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"uint256","name":"amount","type":"uint256"}],"name":"payAmount","outputs":[],"stateMutability":"nonpayable","type":"functi on"},{"inputs":[],"name":"payFixed","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"renounceOwnership","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"bytes32","name":"role","type":"bytes32"},{"internalType":"address","name":"callerConfirmation","type":"address"}],"name":"renounceRole","outputs":[],"stateMutability":"nonpayable","type":"function"} ty":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"admin","type":"address"}],"name":"revokeAdmin","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"bytes32","name":"role","type":"bytes32"},{"internalType":"address","name":"account","type":"address"}],"name":"revokeRole","outputs":[],"stateMutability":"nonpayable","type": "function"},{"inputs":[{"internalType":"address","name":"newAdmin","type":"address"}],"name":"setAdmin","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"newCommunity","type":"address"}],"name":"setCommunity","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"bool","name":"fixedEnabled","type":"bool"},{"internalType":"uint256","name":"fixedAmount","type":"uint256"},{"internalType":"uint256","name":"burnBps_","type":"uint256"},{"internalType":"uint256","name":"communityBps_","type":"uint256"}],"name":"setExchangeParams","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"bytes4","name":"interfaceId","type":"bytes4"}],"name":"suppor tsInterface","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"symbol","outputs":[{"internalType":"string","name":"","type":"string"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"totalSupply","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","ty pe":"function"},{"inputs":[{"internalType":"address","name":"to","type":"address"},{"internalType":"uint256","name":"value","type":"uint256"}],"name":"transfer","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"from","type":"address"},{"internalType":"address","name":"to","typ e":"address"},{"internalType":"uint256","name":"value","type":"uint256"}],"name":"transferFrom","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"nonpayable","type":"function"},{"inputs":[{"internalType":"address","name":"newOwner","type":"address"}],"name":"transferOwnership","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"unpause","outputs":[],"stateMutability":"nonpayable","type":"function"}]
