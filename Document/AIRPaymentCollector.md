# AIRPaymentCollector

## 作用

用户先对收款合约执行 `approve`，然后由收款员地址调用合约，从用户地址扣除 AIR，并转入收款地址（`treasury`）。

AIR 固定代币地址：

`0xB6f92E39CCCe7E6d49A4DF0E8827C13DE1696e5F`

合约文件：

`AIRPaymentCollector.sol`

合约地址:
`0x7Fb9cEE6dC9CFACb36b2c802b8A6b4339864f665`
## 部署

构造参数：

```solidity
constructor(address treasury_)
```

- `treasury_`：最终收款地址

部署者自动成为 `owner`，`owner` 默认拥有收款权限，也可以额外设置 `collector`。

## 典型流程

### 1. 用户授权

用户先对 AIR 合约调用：

```solidity
approve(AIRPaymentCollector合约地址, amount)
```

### 2. 管理员设置收款员

```solidity
setCollector(collector, true)
```

### 3. 收款员执行扣款

```solidity
collect(orderId, payer, amount)
```

- `orderId`：订单号哈希，推荐后端生成唯一值
- `payer`：付款用户地址
- `amount`：扣款金额，AIR 最小单位（18 位）

如果你手里是字符串订单号，可以先调用：

```solidity
hashOrderNo("ORDER-20260419-001")
```

## 关键查询函数

### 预检查

```solidity
previewCollect(orderId, payer, amount)
```

返回：

- `executable`：是否可以执行
- `code`：错误码，`0` 表示通过
- `balance`：用户当前余额
- `allowance_`：用户当前授权额度
- `recipient`：当前收款地址

### 查询订单

```solidity
getOrder(orderId)
```

可查询已扣款订单的付款人、收款人、操作人、金额和时间。

### 错误码说明

```solidity
explainErrorCode(code)
```

## 错误码

| code | 常量 | 说明 |
|---|---|---|
| 1 | `ERR_NOT_OWNER` | 调用者不是 owner |
| 2 | `ERR_NOT_COLLECTOR` | 调用者不是授权收款员 |
| 3 | `ERR_CONTRACT_PAUSED` | 合约已暂停 |
| 4 | `ERR_INVALID_TREASURY` | 收款地址为空 |
| 5 | `ERR_INVALID_COLLECTOR` | 收款员地址为空 |
| 6 | `ERR_INVALID_NEW_OWNER` | 新 owner 地址为空 |
| 7 | `ERR_INVALID_PAYER` | 付款人地址为空 |
| 8 | `ERR_ZERO_AMOUNT` | 扣款金额为 0 |
| 9 | `ERR_EMPTY_ORDER_ID` | 订单号为空 |
| 10 | `ERR_ORDER_ALREADY_USED` | 订单号已使用，防重复扣款 |
| 11 | `ERR_INSUFFICIENT_ALLOWANCE` | 用户授权额度不足 |
| 12 | `ERR_INSUFFICIENT_BALANCE` | 用户 AIR 余额不足 |
| 13 | `ERR_TOKEN_TRANSFER_FAILED` | AIR `transferFrom` 执行失败 |
| 14 | `ERR_ORDER_NOT_FOUND` | 订单记录不存在 |

## 管理函数

```solidity
transferOwnership(newOwner)
setTreasury(newTreasury)
setCollector(collector, enabled)
pause()
unpause()
```

## 事件

- `PaymentCollected(orderId, payer, recipient, operator, amount)`
- `CollectorUpdated(collector, enabled)`
- `TreasuryUpdated(oldTreasury, newTreasury)`
- `OwnershipTransferred(oldOwner, newOwner)`
- `Paused(operator)`
- `Unpaused(operator)`
