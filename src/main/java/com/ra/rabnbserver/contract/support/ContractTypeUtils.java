package com.ra.rabnbserver.contract.support;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 合约参数类型转换工具
 */
public final class ContractTypeUtils {

    private ContractTypeUtils() {
    }

    /**
     * 地址类型
     *
     * @param address 地址字符串
     * @return Address
     */
    public static Address address(String address) {
        return new Address(address);
    }

    /**
     * uint256 类型
     *
     * @param value 数值
     * @return Uint256
     */
    public static Uint256 uint256(BigInteger value) {
        return new Uint256(value);
    }

    /**
     * uint256 类型
     *
     * @param value 数值
     * @return Uint256
     */
    public static Uint256 uint256(long value) {
        return new Uint256(BigInteger.valueOf(value));
    }

    /**
     * uint8 类型
     *
     * @param value 数值
     * @return Uint8
     */
    public static Uint8 uint8(int value) {
        return new Uint8(BigInteger.valueOf(value));
    }

    /**
     * bytes32 类型（输入 hex 字符串）
     *
     * @param hexStr 16 进制字符串（可带 0x）
     * @return Bytes32
     */
    public static Bytes32 bytes32(String hexStr) {
        byte[] raw = Numeric.hexStringToByteArray(hexStr);
        byte[] fixed = new byte[32];
        int copyStart = Math.max(0, raw.length - 32);
        int copyLen = Math.min(raw.length, 32);
        System.arraycopy(raw, copyStart, fixed, 32 - copyLen, copyLen);
        return new Bytes32(fixed);
    }

    /**
     * 动态数组（Address）
     *
     * @param addresses 地址列表
     * @return 动态数组
     */
    public static DynamicArray<Address> addressArray(List<String> addresses) {
        List<Address> list = new ArrayList<>();
        for (String addr : addresses) {
            list.add(address(addr));
        }
        return new DynamicArray<>(Address.class, list);
    }

    /**
     * 动态数组（Uint256）
     *
     * @param values 数值列表
     * @return 动态数组
     */
    public static DynamicArray<Uint256> uint256Array(List<BigInteger> values) {
        List<Uint256> list = new ArrayList<>();
        for (BigInteger value : values) {
            list.add(uint256(value));
        }
        return new DynamicArray<>(Uint256.class, list);
    }

    /**
     * 字符串类型
     *
     * @param value 字符串
     * @return Utf8String
     */
    public static Utf8String utf8(String value) {
        return new Utf8String(value);
    }
}
