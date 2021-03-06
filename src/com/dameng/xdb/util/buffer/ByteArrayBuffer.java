/*
 * @(#)ByteArrayBuffer.java, 2018年9月5日 上午9:10:59
 *
 * Copyright (c) 2000-2018, 达梦数据库有限公司.
 * All rights reserved.
 */
package com.dameng.xdb.util.buffer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;

import com.dameng.xdb.util.ByteUtil;

/**
 * 在这里加入功能说明
 *
 * @author ychao
 * @version $Revision: $, $Author: $, $Date: $
 */
public class ByteArrayBuffer extends Buffer
{
    public Node firstNode; // first buffer node

    public Node lastNode; // last buffer node

    public Node currentNode; // current buffer node

    public int count; // total buffer nodes

    ByteArrayBuffer(int capacity)
    {
        Node node = new Node(capacity);
        this.firstNode = node;
        this.lastNode = node;
        this.currentNode = node;
        this.count = 1;
    }

    ByteArrayBuffer(byte[] bytes, int offset, int length)
    {
        Node node = new Node(bytes, offset, length);
        this.firstNode = node;
        this.lastNode = node;
        this.currentNode = node;
        this.count = 1;
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder("[" + count + "]: ");
        Node node = firstNode;
        while (node != null)
        {
            if (node != firstNode)
            {
                str.append(" <-> ");
            }
            if (node == currentNode)
            {
                str.append("*");
            }
            str.append("{");
            str.append(node);
            str.append("}");
            node = node.next;
        }
        return str.toString();
    }

    @Override
    public void clear()
    {
        Node node = firstNode;
        while (node != null)
        {
            node.clear();
            node = node.next;
        }
        currentNode = firstNode;
    }

    @Override
    public void truncate(int offset)
    {
        // 定位目标节点
        Node node = locate(offset);
        offset = (Integer)node.info;

        // 修正目标节点
        node.offset = offset;
        node.size = offset;
        currentNode = node;

        // 清理后续节点
        node = node.next;
        while (node != null)
        {
            node.clear();
            node = node.next;
        }
    }

    @Override
    public int length()
    {
        int length = 0;
        Node node = firstNode;
        while (node != null)
        {
            length += node.size;
            node = node.next;
        }
        return length;
    }

    @Override
    public void flip()
    {
        Node node = firstNode;
        while (node != null)
        {
            node.flip();
            node = node.next;
        }
        currentNode = firstNode;
    }

    @Override
    public void fill(int len)
    {
        while (len > 0)
        {
            len -= currentNode.fill(len);
            if (len == 0)
            {
                break;
            }
            currentNode = currentNode.next;
            if (currentNode == null)
            {
                extend(len);
            }
        }
    }

    @Override
    public void skip(int len)
    {
        while (len > 0)
        {
            len -= currentNode.skip(len);
            if (len == 0)
            {
                break;
            }
            currentNode = currentNode.next;
            if (currentNode == null)
            {
                throw new RuntimeException("buffer index out of range");
            }
        }
    }

    @Override
    public long offset()
    {
        int offset = 0;

        Node node = firstNode;
        while (node != null)
        {
            offset += node.offset;
            if (node == currentNode)
            {
                break;
            }
            node = node.next;
        }

        return offset;
    }

    public Node locate(int offset)
    {
        Node node = firstNode;
        while (node != null)
        {
            if (offset < node.size)
            {
                break;
            }
            offset -= node.size;
            node = node.next;
        }
        if (node == null)
        {
            throw new RuntimeException("buffer index out of range");
        }

        node.info = offset;

        return node;
    }

    public void extend(int len)
    {
        int maybe = 2 * lastNode.bytes.length;
        len = len > maybe ? len : maybe;
        currentNode = new Node(len);
        currentNode.prev = lastNode;
        lastNode.next = currentNode;
        lastNode = currentNode;
        count++;
    }

    /*---------------------------------------------------*/
    private int write(long val, int len)
    {
        int wtimes = 0;
        while (len > 0)
        {
            int leave = currentNode.leave(true);
            if (len <= leave)
            {
                for (int i = 0; i < len; ++i)
                {
                    currentNode.bytes[currentNode.offset++] = (byte)(val >> ((wtimes++) * 8));
                    currentNode.size++;
                }
                break;
            }
            else
            {
                for (int i = 0; i < leave; ++i)
                {
                    currentNode.bytes[currentNode.offset++] = (byte)(val >> ((wtimes++) * 8));
                    currentNode.size++;
                }
                len -= leave;
                currentNode = currentNode.next;
                if (currentNode == null)
                {
                    extend(len);
                }
            }
        }
        return wtimes;
    }

    @Override
    public int writeBoolean(boolean b)
    {
        return writeByte(b ? (byte)1 : (byte)0);
    }

    @Override
    public int writeByte(byte b)
    {
        if (currentNode.leave(true) >= 1)
        {
            ByteUtil.setByte(currentNode.bytes, currentNode.offset, b);
            currentNode.offset += 1;
            currentNode.size += 1;
            return 1;
        }
        return write(b, 1);
    }

    @Override
    public int writeShort(short s)
    {
        if (currentNode.leave(true) >= 2)
        {
            ByteUtil.setShort(currentNode.bytes, currentNode.offset, s);
            currentNode.size += 2;
            currentNode.offset += 2;
            return 2;
        }
        return write(s, 2);
    }

    @Override
    public int writeInt(int i)
    {
        if (currentNode.leave(true) >= 4)
        {
            ByteUtil.setInt(currentNode.bytes, currentNode.offset, i);
            currentNode.size += 4;
            currentNode.offset += 4;
            return 4;
        }
        return write(i, 4);
    }

    @Override
    public int writeUB1(int i)
    {
        if (currentNode.leave(true) >= 1)
        {
            ByteUtil.setUB1(currentNode.bytes, currentNode.offset, i);
            currentNode.offset += 1;
            currentNode.size += 1;
            return 1;
        }
        return write(i, 1);
    }

    @Override
    public int writeUB2(int i)
    {
        if (currentNode.leave(true) >= 2)
        {
            ByteUtil.setUB2(currentNode.bytes, currentNode.offset, i);
            currentNode.size += 2;
            currentNode.offset += 2;
            return 2;
        }
        return write(i, 2);
    }

    @Override
    public int writeUB3(int i)
    {
        if (currentNode.leave(true) >= 3)
        {
            ByteUtil.setUB3(currentNode.bytes, currentNode.offset, i);
            currentNode.size += 3;
            currentNode.offset += 3;
            return 3;
        }
        return write(i, 3);
    }

    @Override
    public int writeUB4(long l)
    {
        if (currentNode.leave(true) >= 4)
        {
            ByteUtil.setUB4(currentNode.bytes, currentNode.offset, l);
            currentNode.size += 4;
            currentNode.offset += 4;
            return 4;
        }
        return write(l, 4);
    }

    @Override
    public int writeLong(long l)
    {
        if (currentNode.leave(true) >= 8)
        {
            ByteUtil.setLong(currentNode.bytes, currentNode.offset, l);
            currentNode.size += 8;
            currentNode.offset += 8;
            return 8;
        }
        return write(l, 8);
    }

    @Override
    public int writeFloat(float f)
    {
        return writeInt(Float.floatToIntBits(f));
    }

    @Override
    public int writeDouble(double d)
    {
        return writeLong(Double.doubleToLongBits(d));
    }

    @Override
    public int writeBytes(byte[] srcBytes)
    {
        return writeBytes(srcBytes, 0, srcBytes.length);
    }

    @Override
    public int writeBytes(byte[] srcBytes, int srcOffset, int len)
    {
        int total = 0;
        while (len > 0)
        {
            int wlen = currentNode.write(srcBytes, srcOffset, len);
            len -= wlen;
            total += wlen;
            srcOffset += wlen;
            if (len == 0)
            {
                break;
            }
            currentNode = currentNode.next;
            if (currentNode == null)
            {
                extend(len);
            }
        }
        return total;
    }

    @Override
    public int writeBytesWithLength(byte[] srcBytes, int srcOffset, int len)
    {
        return writeInt(len) + writeBytes(srcBytes, srcOffset, len);
    }

    @Override
    public int writeBytesWithLength1(byte[] srcBytes, int srcOffset, int len)
    {
        return writeUB1(len) + writeBytes(srcBytes, srcOffset, len);
    }

    @Override
    public int writeBytesWithLength2(byte[] srcBytes, int srcOffset, int len)
    {
        return writeUB2(len) + writeBytes(srcBytes, srcOffset, len);
    }

    @Override
    public int writeStringWithLength(String str, String encoding)
    {
        byte[] strBytes = ByteUtil.getBytes(str, encoding);
        return writeBytesWithLength(strBytes, 0, strBytes.length);
    }

    @Override
    public int writeStringWithLength1(String str, String encoding)
    {
        byte[] strBytes = ByteUtil.getBytes(str, encoding);
        return writeBytesWithLength1(strBytes, 0, strBytes.length);
    }

    @Override
    public int writeStringWithLength2(String str, String encoding)
    {
        byte[] strBytes = ByteUtil.getBytes(str, encoding);
        return writeBytesWithLength2(strBytes, 0, strBytes.length);
    }

    @Override
    public int writeStringWithNTS(String str, String encoding)
    {
        byte[] strBytes = ByteUtil.getBytes(str, encoding);
        return writeBytes(strBytes, 0, strBytes.length) + writeByte((byte)'\0');
    }

    /*---------------------------------------------------*/
    private long read(int len)
    {
        long val = 0;
        int rtimes = 0;
        while (len > 0)
        {
            int leave = currentNode.leave(false);
            if (len <= leave)
            {
                for (int i = 0; i < len; ++i)
                {
                    val |= (long)(currentNode.bytes[currentNode.offset++] & 0xff) << ((rtimes++) * 8);
                }
                break;
            }
            else
            {
                for (int i = 0; i < leave; ++i)
                {
                    val |= (long)(currentNode.bytes[currentNode.offset++] & 0xff) << ((rtimes++) * 8);
                }
                len -= leave;
                currentNode = currentNode.next;
                if (currentNode == null)
                {
                    throw new RuntimeException("buffer index out of range");
                }
            }
        }
        return val;
    }

    @Override
    public boolean readBoolean()
    {
        return readByte() == 0 ? false : true;
    }

    @Override
    public byte readByte()
    {
        if (currentNode.leave(false) >= 1)
        {
            byte b = ByteUtil.getByte(currentNode.bytes, currentNode.offset);
            currentNode.offset += 1;
            return b;
        }
        return (byte)read(1);
    }

    @Override
    public short readShort()
    {
        if (currentNode.leave(false) >= 2)
        {
            short s = ByteUtil.getShort(currentNode.bytes, currentNode.offset);
            currentNode.offset += 2;
            return s;
        }
        return (short)read(2);
    }

    @Override
    public int readInt()
    {
        if (currentNode.leave(false) >= 4)
        {
            int i = ByteUtil.getInt(currentNode.bytes, currentNode.offset);
            currentNode.offset += 4;
            return i;
        }
        return (int)read(4);
    }

    @Override
    public long readLong()
    {
        if (currentNode.leave(false) >= 8)
        {
            long l = ByteUtil.getByte(currentNode.bytes, currentNode.offset);
            currentNode.size += 8;
            currentNode.offset += 8;
            return l;
        }
        return read(8);
    }

    @Override
    public float readFloat()
    {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble()
    {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public int readUB1()
    {
        if (currentNode.leave(false) >= 1)
        {
            byte b = ByteUtil.getByte(currentNode.bytes, currentNode.offset);
            currentNode.offset += 1;
            return b;
        }
        return (int)read(1);
    }

    @Override
    public int readUB2()
    {
        if (currentNode.leave(false) >= 2)
        {
            int i = ByteUtil.getUB2(currentNode.bytes, currentNode.offset);
            currentNode.offset += 2;
            return i;
        }
        return (int)read(2);
    }

    @Override
    public int readUB3()
    {
        if (currentNode.leave(false) >= 3)
        {
            int i = ByteUtil.getUB3(currentNode.bytes, currentNode.offset);
            currentNode.offset += 3;
            return i;
        }
        return (int)read(3);
    }

    @Override
    public long readUB4()
    {
        if (currentNode.leave(false) >= 4)
        {
            long l = ByteUtil.getUB4(currentNode.bytes, currentNode.offset);
            currentNode.offset += 4;
            return l;
        }
        return read(4);
    }

    @Override
    public byte[] readBytes(int len)
    {
        int destOffset = 0;
        byte[] destBytes = new byte[len];
        while (len > 0)
        {
            int rlen = currentNode.read(destBytes, destOffset, len);
            len -= rlen;
            destOffset += rlen;
            if (len == 0)
            {
                break;
            }
            currentNode = currentNode.next;
            if (currentNode == null)
            {
                throw new RuntimeException("buffer index out of range");
            }
        }
        return destBytes;
    }

    @Override
    public byte[] readBytesWithLength()
    {
        return readBytes(readInt());
    }

    @Override
    public byte[] readBytesWithLength1()
    {
        return readBytes(readUB1());
    }

    @Override
    public byte[] readBytesWithLength2()
    {
        return readBytes(readUB2());
    }

    @Override
    public String readString(int len, String encoding)
    {
        byte[] strBytes = readBytes(len);
        return ByteUtil.getString(strBytes, 0, strBytes.length, encoding);
    }

    @Override
    public String readStringWithLength(String encoding)
    {
        byte[] strBytes = readBytesWithLength();
        return ByteUtil.getString(strBytes, 0, strBytes.length, encoding);
    }

    @Override
    public String readStringWithLength1(String encoding)
    {
        byte[] strBytes = readBytesWithLength1();
        return ByteUtil.getString(strBytes, 0, strBytes.length, encoding);
    }

    @Override
    public String readStringWithLength2(String encoding)
    {
        byte[] strBytes = readBytesWithLength2();
        return ByteUtil.getString(strBytes, 0, strBytes.length, encoding);
    }

    /*---------------------------------------------------*/
    private int set(Node node, int offset, long val, int len)
    {
        int stimes = 0;
        while (len > 0)
        {
            int leave = node.size - offset;
            if (len <= leave)
            {
                for (int i = 0; i < len; ++i)
                {
                    node.bytes[offset++] = (byte)(val >> ((stimes++) * 8));
                }
                break;
            }
            else
            {
                for (int i = 0; i < leave; ++i)
                {
                    node.bytes[offset++] = (byte)(val >> ((stimes++) * 8));
                }
                len -= leave;
                offset = 0;
                node = node.next;
                if (node == null)
                {
                    throw new RuntimeException("buffer index out of range");
                }
            }
        }
        return stimes;
    }

    @Override
    public int setBoolean(int offset, boolean b)
    {
        return setByte(offset, b ? (byte)1 : (byte)0);
    }

    @Override
    public int setByte(int offset, byte b)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 1)
        {
            return ByteUtil.setByte(node.bytes, offset, b);
        }
        return set(node, offset, b, 1);
    }

    @Override
    public int setShort(int offset, short s)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 2)
        {
            return ByteUtil.setShort(node.bytes, offset, s);
        }
        return set(node, offset, s, 2);
    }

    @Override
    public int setInt(int offset, int i)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 4)
        {
            return ByteUtil.setInt(node.bytes, offset, i);
        }
        return set(node, offset, i, 4);
    }

    @Override
    public int setLong(int offset, long l)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 1)
        {
            return ByteUtil.setLong(node.bytes, offset, l);
        }
        return set(node, offset, l, 8);
    }

    @Override
    public int setFloat(int offset, float f)
    {
        return setInt(offset, Float.floatToIntBits(f));
    }

    @Override
    public int setDouble(int offset, double d)
    {
        return setLong(offset, Double.doubleToLongBits(d));
    }

    @Override
    public int setUB1(int offset, int i)
    {
        return setByte(offset, (byte)i);
    }

    @Override
    public int setUB2(int offset, int i)
    {
        return setShort(offset, (short)i);
    }

    @Override
    public int setUB3(int offset, int i)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 3)
        {
            return ByteUtil.setUB3(node.bytes, offset, i);
        }
        return 3;
    }

    @Override
    public int setUB4(int offset, long l)
    {
        return setInt(offset, (int)l);
    }

    @Override
    public int setBytes(int offset, byte[] srcBytes)
    {
        return setBytes(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setBytes(int offset, byte[] srcBytes, int srcOffset, int len)
    {
        // 定位目标节点
        Node node = locate(offset);
        offset = (Integer)node.info;

        int total = 0;
        while (len > 0)
        {
            int slen = node.set(offset, srcBytes, srcOffset, len);
            len -= slen;
            total += slen;
            offset = 0;
            srcOffset += slen;
            if (len == 0)
            {
                break;
            }
            node = node.next;
            if (node == null)
            {
                throw new RuntimeException("buffer index out of range");
            }
        }
        return total;
    }

    @Override
    public int setBytesWithLength(int offset, byte[] srcBytes)
    {
        return setBytesWithLength(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setBytesWithLength(int offset, byte[] srcBytes, int srcOffset, int len)
    {
        offset += setInt(offset, len);
        return 4 + setBytes(offset, srcBytes, srcOffset, len);
    }

    @Override
    public int setBytesWithLength1(int offset, byte[] srcBytes)
    {
        return setBytesWithLength1(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setBytesWithLength1(int offset, byte[] srcBytes, int srcOffset, int len)
    {
        offset += setUB1(offset, len);
        return 1 + setBytes(offset, srcBytes, srcOffset, len);
    }

    @Override
    public int setBytesWithLength2(int offset, byte[] srcBytes)
    {
        return setBytesWithLength2(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setBytesWithLength2(int offset, byte[] srcBytes, int srcOffset, int len)
    {
        offset += setUB2(offset, len);
        return 2 + setBytes(offset, srcBytes, srcOffset, len);
    }

    @Override
    public int setStringWithLength(int offset, String str, String encoding)
    {
        byte[] srcBytes = ByteUtil.getBytes(str, encoding);
        return setBytesWithLength(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setStringWithLength1(int offset, String str, String encoding)
    {
        byte[] srcBytes = ByteUtil.getBytes(str, encoding);
        return setBytesWithLength1(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setStringWithLength2(int offset, String str, String encoding)
    {
        byte[] srcBytes = ByteUtil.getBytes(str, encoding);
        return setBytesWithLength2(offset, srcBytes, 0, srcBytes.length);
    }

    @Override
    public int setStringWithNTS(int offset, String str, String encoding)
    {
        byte[] srcBytes = ByteUtil.getBytes(str, encoding);
        return setBytes(offset, srcBytes, 0, srcBytes.length) + setByte(offset + srcBytes.length, (byte)'\0');
    }

    /*---------------------------------------------------*/
    private long get(Node node, int offset, int len)
    {
        long val = 0;
        int gtimes = 0;
        while (len > 0)
        {
            int leave = node.size - offset;
            if (len <= leave)
            {
                for (int i = 0; i < len; ++i)
                {
                    val |= (long)(node.bytes[offset++] & 0xff) << ((gtimes++) * 8);
                }
                break;
            }
            else
            {
                for (int i = 0; i < leave; ++i)
                {
                    val |= (long)(node.bytes[offset++] & 0xff) << ((gtimes++) * 8);
                }
                len -= leave;
                offset = 0;
                node = node.next;
                if (node == null)
                {
                    throw new RuntimeException("buffer index out of range");
                }
            }
        }
        return val;
    }

    @Override
    public boolean getBoolean(int offset)
    {
        return getByte(offset) == 0 ? true : false;
    }

    @Override
    public byte getByte(int offset)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 1)
        {
            return ByteUtil.getByte(node.bytes, offset);
        }

        return (byte)get(node, offset, 1);
    }

    @Override
    public short getShort(int offset)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 2)
        {
            return ByteUtil.getShort(node.bytes, offset);
        }

        return (short)get(node, offset, 2);
    }

    @Override
    public int getInt(int offset)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 4)
        {
            return ByteUtil.getInt(node.bytes, offset);
        }

        return (byte)get(node, offset, 4);
    }

    @Override
    public long getLong(int offset)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 8)
        {
            return ByteUtil.getLong(node.bytes, offset);
        }

        return get(node, offset, 8);
    }

    @Override
    public float getFloat(int offset)
    {
        return Float.intBitsToFloat(getInt(offset));
    }

    @Override
    public double getDouble(int offset)
    {
        return Double.longBitsToDouble(getLong(offset));
    }

    @Override
    public int getUB1(int offset)
    {
        return getByte(offset) & 0xff;
    }

    @Override
    public int getUB2(int offset)
    {
        return getShort(offset) & 0xffff;
    }

    @Override
    public int getUB3(int offset)
    {
        Node node = locate(offset);
        offset = (Integer)node.info;
        if (node.size - offset >= 3)
        {
            return ByteUtil.getUB3(node.bytes, offset);
        }
        return (int)get(node, offset, 3);
    }

    @Override
    public long getUB4(int offset)
    {
        return getInt(offset) & 0x00000000ffffffffl;
    }

    @Override
    public byte[] getBytes(int offset, int len)
    {
        // 定位目标节点
        Node node = locate(offset);
        offset = (Integer)node.info;

        int destOffset = 0;
        byte[] destBytes = new byte[len];
        while (len > 0)
        {
            int rlen = node.get(offset, destBytes, destOffset, len);
            len -= rlen;
            if (len == 0)
            {
                break;
            }
            offset = 0;
            destOffset += rlen;
            node = node.next;
            if (node == null)
            {
                throw new RuntimeException("buffer index out of range");
            }
        }
        return destBytes;
    }

    @Override
    public byte[] getBytesWithLength(int offset)
    {
        return getBytes(offset + 4, getInt(offset));
    }

    @Override
    public byte[] getBytesWithLength1(int offset)
    {
        return getBytes(offset + 1, getUB1(offset));
    }

    @Override
    public byte[] getBytesWithLength2(int offset)
    {
        return getBytes(offset + 2, getUB2(offset));
    }

    @Override
    public String getStringWithLength(int offset, String encoding)
    {
        byte[] bytes = getBytesWithLength(offset);
        return ByteUtil.getString(bytes, 0, bytes.length, encoding);
    }

    @Override
    public String getStringWithLength1(int offset, String encoding)
    {
        byte[] bytes = getBytesWithLength1(offset);
        return ByteUtil.getString(bytes, 0, bytes.length, encoding);
    }

    @Override
    public String getStringWithLength2(int offset, String encoding)
    {
        byte[] bytes = getBytesWithLength2(offset);
        return ByteUtil.getString(bytes, 0, bytes.length, encoding);
    }

    @Override
    public int load(InputStream is, int len) throws IOException
    {
        int total = 0;
        while (len > 0)
        {
            int llen = currentNode.load(is, len); // 0 <= llen <= len
            total += llen;
            len -= llen;
            if (len == 0)
            {
                break;
            }
            currentNode = currentNode.next;
            if (currentNode == null)
            {
                extend(len);
            }
        }
        return total;
    }

    @Override
    public void flush(OutputStream os) throws IOException
    {
        Node node = firstNode;
        do
        {
            os.write(node.bytes, 0, node.size);
            node = node.next;
        } while (node != null);

        os.flush();
    }

    @Override
    public int load(ReadableByteChannel channel, int len) throws IOException
    {
        throw new RuntimeException("not support interface");
    }

    @Override
    public void flush(WritableByteChannel channel) throws IOException
    {
        throw new RuntimeException("not support interface");
    }

    /*---------------------------------------------------*/
    public static class Node
    {
        public byte[] bytes; // 数据存放区

        public int offset; // 当前节点操作点偏移

        public int size; // 当前存放数据大小(offset <= size <= capacity)

        public Object info; // 辅助信息字段

        public Node prev; // previous node

        public Node next; // next node

        Node(int capacity)
        {
            this.bytes = new byte[capacity];
            this.offset = 0;
            this.size = 0;
            this.prev = null;
            this.next = null;
            this.info = null;
        }

        Node(byte[] bytes, int offset, int length)
        {
            this.bytes = bytes;
            this.offset = offset;
            this.size = offset + length;
            this.prev = null;
            this.next = null;
            this.info = null;
        }

        @Override
        public String toString()
        {
            return "capacity=" + bytes.length + " offset=" + offset + " size=" + size + " info=" + info;
        }

        /**
         * 浅清理，原始数据还存在
         */
        private void clear()
        {
            this.offset = 0;
            this.size = 0;
        }

        /**
         * 剩余空间大小
         */
        private int leave(boolean write)
        {
            return write ? bytes.length - size : size - offset;
        }

        /**
         * 写转读
         */
        private void flip()
        {
            this.offset = 0;
        }

        /**
         * 指定长度以0填充，返回实际填充长度
         */
        private int fill(int len)
        {
            int leave = leave(true);
            if (len > leave)
            {
                len = leave;
            }
            Arrays.fill(bytes, offset, offset + len, (byte)0);
            offset += len;
            size += len;
            return len;
        }

        /**
         * 跳过指定长度，返回实际跳过长度
         */
        private int skip(int len)
        {
            int leave = leave(false);
            if (len > leave)
            {
                len = leave;
            }
            offset += len;
            return len;
        }

        /**
         * 写数据，返回实际写入数据长度
         */
        private int write(byte[] srcBytes, int srcOffset, int len)
        {
            int leave = leave(true);
            if (len > leave)
            {
                len = leave;
            }
            System.arraycopy(srcBytes, srcOffset, bytes, offset, len);
            offset += len;
            size += len;
            return len;
        }

        /**
         * 设置数据，返回实际设置数据长度
         */
        private int set(int offset, byte[] srcBytes, int srcOffset, int len)
        {
            int leave = size - offset;
            if (len > leave)
            {
                len = leave;
            }
            System.arraycopy(srcBytes, srcOffset, bytes, offset, len);
            return len;
        }

        /**
         * 读数据，返回实际读取数据长度
         */
        private int read(byte[] destBytes, int destOffset, int len)
        {
            int leave = leave(false);
            if (len > leave)
            {
                len = leave;
            }
            System.arraycopy(bytes, offset, destBytes, destOffset, len);
            offset += len;
            return len;
        }

        /**
         * 读数据，返回实际读取数据长度
         */
        private int get(int offset, byte[] destBytes, int destOffset, int len)
        {
            int leave = size - offset;
            if (len > leave)
            {
                len = leave;
            }
            System.arraycopy(bytes, offset, destBytes, destOffset, len);
            return len;
        }

        /**
         * 从指定的流中读取指定长度的数据，返回实际读取的数据长度
         */
        private int load(InputStream is, int len) throws IOException
        {
            int leave = leave(true);
            if (len > leave)
            {
                len = leave;
            }

            int read = 0;
            while (read < len)
            {
                int ret = is.read(bytes, offset + read, len - read);
                if (ret < 0)
                {
                    throw new EOFException();
                }
                read += ret;
            }
            offset += read;
            size += read;
            return read;
        }
    }
}
