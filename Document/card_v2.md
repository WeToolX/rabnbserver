# Card V2（SingleCard1155）详细说明文档

本文档对应合约文件：`/Volumes/SSD_1/DevelopmentProject/dapp-contracts/contracts/card_v2.sol`

---

## 一、合约概述

* 标准：ERC-1155（OpenZeppelin v5）
* 扩展：`ERC1155Supply`、`ERC1155Burnable`、`AccessControl`
* 合约名：`SingleCard1155`
* 主要功能：
  * 发行三种固定卡牌（铜/银/金）
  * 管理员分发
  * 管理员代用户销毁并记录订单
  * 统计销毁数量（按地址 + 卡牌 ID）

---

## 二、常量与角色

### 1. 卡牌 ID

* `COPPER_ID = 1`
* `SILVER_ID = 2`
* `GOLD_ID = 3`

### 2. 角色权限

* `DEFAULT_ADMIN_ROLE`
  * 部署者自动拥有
  * 可授予/撤销 `ADMIN_ROLE`
* `ADMIN_ROLE`
  * 允许分发 `distribute`
  * 允许代烧 `burnWithOrder`

### 3. 名称与符号

* `name`、`symbol` 在部署时传入并保存
* 仅用于前端展示，不影响 ERC-1155 逻辑

---

## 三、存储结构

### 1. 销毁统计

```solidity
mapping(address => mapping(uint256 => uint256)) public burnedAmount;
```

* 含义：`burnedAmount[user][id]` 记录用户对某个 ID 的累计销毁数量
* 更新时机：`_update` 内部，当 `to == address(0)` 且 `from != address(0)` 时累加

### 2. 订单使用标记

```solidity
mapping(bytes32 => bool) public orderUsed;
```

* `true` 表示该订单号已被使用
* 只在 `burnWithOrder` 成功时写入

### 3. 订单记录

```solidity
struct OrderInfo {
    address user;
    uint256 id;
    uint256 amount;
    uint256 timestamp;
}

mapping(bytes32 => OrderInfo) private _orders;
```

* `getOrder(orderId)` 直接返回该结构
* 若订单不存在，则返回默认零值（地址为 0）

---

## 四、URI 规则

每个卡牌 ID 固定对应一个 CID：

* `COPPER_ID` → `bafkreicowjen27rapmlogufkjfokccalqbzsbygnabkzts5mcxr62te7ke`
* `SILVER_ID` → `bafkreieahtzw7bfe7j4tkbm7uxeppvnvh4qlpujnrqqpxrwn325d7jm4xy`
* `GOLD_ID` → `bafkreigqf5rkeoxlruj7cqcadponbmfwlpmrf33wvk426qayknltbasq4y`

`uri(id)` 会根据 ID 返回固定 URI，不支持动态修改。

---

## 五、核心接口说明

### 1. setAdmin

```solidity
function setAdmin(address admin, bool enabled) external onlyRole(DEFAULT_ADMIN_ROLE);
```

* 作用：授予/撤销管理员权限
* 权限：仅 `DEFAULT_ADMIN_ROLE`

### 2. distribute

```solidity
function distribute(address to, uint256 id, uint256 amount)
    external
    onlyRole(ADMIN_ROLE);
```

* 作用：管理员分发卡牌
* 规则：
  * `id` 必须是 1/2/3
  * `amount > 0`

### 3. burnWithOrder

```solidity
function burnWithOrder(address from, uint256 id, uint256 amount, bytes32 orderId)
    external
    onlyRole(ADMIN_ROLE);
```

* 作用：管理员代用户销毁卡牌，并写入订单记录
* 前置条件：
  * `id` 必须是 1/2/3
  * `amount > 0`
  * `orderId` 未被使用
  * 用户已对管理员 `setApprovalForAll`
* 逻辑步骤：
  1. 校验参数与授权
  2. 标记 `orderUsed[orderId] = true`
  3. 写入 `_orders[orderId]`
  4. `burn(from, id, amount)`
  5. 触发事件 `BurnWithOrder`

### 4. getOrder

```solidity
function getOrder(bytes32 orderId)
    external
    view
    returns (address user, uint256 id, uint256 amount, uint256 timestamp);
```

* 查询订单明细
* 若订单不存在，返回全零值

---

## 六、事件说明

### BurnWithOrder

```
event BurnWithOrder(
    bytes32 indexed orderId,
    address indexed user,
    uint256 indexed id,
    uint256 amount
);
```

* 在 `burnWithOrder` 成功后触发
* 用于链上审计订单销毁

> 继承的 ERC-1155 事件（如 `TransferSingle`、`TransferBatch`、`ApprovalForAll`）同样会触发。

---

## 七、错误与约束清单

### distribute

* `require(id >= 1 && id <= 3, "invalid id")`
* `require(amount > 0, "amount=0")`

### burnWithOrder

* `require(id >= 1 && id <= 3, "invalid id")`
* `require(amount > 0, "amount=0")`
* `require(!orderUsed[orderId], "order used")`
* `require(isApprovedForAll(from, msg.sender), "not approved")`
* 继承 `ERC1155Burnable` 的余额校验：
  * 余额不足会 `revert`（错误信息由 OZ 库抛出）

### uri

* 非 1/2/3 的 ID 会 `revert("invalid id")`

---

## 八、销毁统计逻辑

在 `_update` 中判断：

```solidity
if (to == address(0) && from != address(0)) {
    burnedAmount[from][ids[i]] += values[i];
}
```

* 只有**真正销毁**时才累计
* 普通转账不会影响 `burnedAmount`

---

## 九、接口清单（含继承）

### 合约自定义接口
* `setAdmin`
* `distribute`
* `burnWithOrder`
* `getOrder`
* `uri`

### ERC-1155 标准接口
* `balanceOf`
* `balanceOfBatch`
* `setApprovalForAll`
* `isApprovedForAll`
* `safeTransferFrom`
* `safeBatchTransferFrom`

### ERC-1155 Supply 扩展
* `totalSupply(id)`
* `exists(id)`

---

## 十、前端接入与 ABI 调用示例

本节给出前端接入要点与常用调用示例（ethers.js），并说明返回字段结构。

### 1. ABI 接入要点

* 合约名：`SingleCard1155`
* 需要暴露的自定义方法：
  * `setAdmin`
  * `distribute`
  * `burnWithOrder`
  * `getOrder`
  * `uri`
* 需要的 ERC-1155 标准方法：
  * `balanceOf`
  * `balanceOfBatch`
  * `setApprovalForAll`
  * `isApprovedForAll`
  * `safeTransferFrom`

### 2. getOrder 返回字段结构

`getOrder(bytes32 orderId)` 返回：

```text
user      (address)
id        (uint256)
amount    (uint256)
timestamp (uint256)
```

当订单不存在时，返回全零值：
* `user = 0x0000000000000000000000000000000000000000`
* `id = 0`
* `amount = 0`
* `timestamp = 0`

### 3. 事件结构

**BurnWithOrder**
```
orderId (bytes32, indexed)
user    (address, indexed)
id      (uint256, indexed)
amount  (uint256)
```

### 4. ethers.js 调用示例

#### 4.1 初始化合约

```ts
import { ethers } from "ethers";

const provider = new ethers.JsonRpcProvider(RPC_URL);
const signer = new ethers.Wallet(PRIVATE_KEY, provider);
const contract = new ethers.Contract(CONTRACT_ADDRESS, ABI, signer);
```

#### 4.2 查询余额

```ts
const balance = await contract.balanceOf(userAddress, 1); // COPPER_ID=1
```

#### 4.3 设置授权（用户端）

```ts
// 用户授权管理员
await contract.setApprovalForAll(adminAddress, true);
```

#### 4.4 管理员分发卡牌

```ts
await contract.distribute(userAddress, 1, 10);
```

#### 4.5 管理员代烧并记录订单

```ts
const orderId = ethers.id("order-2026-0001"); // bytes32
await contract.burnWithOrder(userAddress, 3, 1, orderId);
```

#### 4.6 查询订单

```ts
const order = await contract.getOrder(orderId);
// order[0] = user
// order[1] = id
// order[2] = amount
// order[3] = timestamp
```

#### 4.7 监听事件

```ts
contract.on("BurnWithOrder", (orderId, user, id, amount) => {
  console.log("BurnWithOrder", { orderId, user, id, amount });
});
```

---

## 十一、调用示例

### 1. 设置管理员

```solidity
setAdmin(0xAdminAddress, true);
```

### 2. 分发卡牌

```solidity
distribute(0xUser, COPPER_ID, 10);
```

### 3. 用户授权管理员

```solidity
setApprovalForAll(0xAdminAddress, true);
```

### 4. 管理员代烧并记录订单

```solidity
burnWithOrder(0xUser, GOLD_ID, 1, 0x...orderId);
```

### 5. 查询订单

```solidity
getOrder(0x...orderId);
```

---

## 十一、注意事项

1. 订单号 `orderId` 为 `bytes32`，必须保证唯一性。
2. `burnWithOrder` 会强制检查管理员是否获得用户授权。
3. `distribute` 无上限，注意权限管理。
4. `name` / `symbol` 仅用于前端展示，不影响 ERC-1155 标准。
5. `getOrder` 不会抛错，订单不存在时返回默认零值。
