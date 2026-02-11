// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
import "@openzeppelin/contracts/token/ERC1155/extensions/ERC1155Supply.sol";
import "@openzeppelin/contracts/token/ERC1155/extensions/ERC1155Burnable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

contract SingleCard1155 is ERC1155, ERC1155Supply, ERC1155Burnable, AccessControl {
    uint256 public constant COPPER_ID = 1;
    uint256 public constant SILVER_ID = 2;
    uint256 public constant GOLD_ID = 3;

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    string public name;
    string public symbol;

    mapping(address => mapping(uint256 => uint256)) public burnedAmount;
    mapping(bytes32 => bool) public orderUsed;

    struct OrderInfo {
        address user;
        uint256 id;
        uint256 amount;
        uint256 timestamp;
    }

    mapping(bytes32 => OrderInfo) private _orders;

    event BurnWithOrder(bytes32 indexed orderId, address indexed user, uint256 indexed id, uint256 amount);

    constructor(string memory name_, string memory symbol_, address initialAdmin) ERC1155("") {
        name = name_;
        symbol = symbol_;

        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        if (initialAdmin != address(0)) {
            _grantRole(ADMIN_ROLE, initialAdmin);
        }
    }

    function uri(uint256 id) public pure override returns (string memory) {
        if (id == COPPER_ID) {
            return "https://gateway.lighthouse.storage/ipfs/bafkreicowjen27rapmlogufkjfokccalqbzsbygnabkzts5mcxr62te7ke";
        }
        if (id == SILVER_ID) {
            return "https://gateway.lighthouse.storage/ipfs/bafkreieahtzw7bfe7j4tkbm7uxeppvnvh4qlpujnrqqpxrwn325d7jm4xy";
        }
        if (id == GOLD_ID) {
            return "https://gateway.lighthouse.storage/ipfs/bafkreigqf5rkeoxlruj7cqcadponbmfwlpmrf33wvk426qayknltbasq4y";
        }
        revert("invalid id");
    }

    function setAdmin(address admin, bool enabled) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (enabled) {
            _grantRole(ADMIN_ROLE, admin);
        } else {
            _revokeRole(ADMIN_ROLE, admin);
        }
    }

    function distribute(address to, uint256 id, uint256 amount) external onlyRole(ADMIN_ROLE) {
        require(id >= 1 && id <= 3, "invalid id");
        require(amount > 0, "amount=0");
        _mint(to, id, amount, "");
    }

    function burnWithOrder(address from, uint256 id, uint256 amount, bytes32 orderId) external onlyRole(ADMIN_ROLE) {
        require(id >= 1 && id <= 3, "invalid id");
        require(amount > 0, "amount=0");
        require(!orderUsed[orderId], "order used");
        require(isApprovedForAll(from, msg.sender), "not approved");

        orderUsed[orderId] = true;

        _orders[orderId] = OrderInfo({
            user: from,
            id: id,
            amount: amount,
            timestamp: block.timestamp
        });

        burn(from, id, amount);
        emit BurnWithOrder(orderId, from, id, amount);
    }

    function getOrder(bytes32 orderId) external view returns (address user, uint256 id, uint256 amount, uint256 timestamp) {
        OrderInfo memory o = _orders[orderId];
        return (o.user, o.id, o.amount, o.timestamp);
    }

    function _update(address from, address to, uint256[] memory ids, uint256[] memory values) internal override(ERC1155, ERC1155Supply) {
        super._update(from, to, ids, values);

        if (to == address(0) && from != address(0)) {
            for (uint256 i = 0; i < ids.length; i++) {
                burnedAmount[from][ids[i]] += values[i];
            }
        }
    }

    function supportsInterface(bytes4 interfaceId) public view override(ERC1155, AccessControl) returns (bool) {
        return super.supportsInterface(interfaceId);
    }
}
