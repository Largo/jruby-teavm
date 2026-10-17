# compat: TeaVM 0.16.0's FileInputStream.read() sign-extends the byte (returns -1..-128 for 0x80..0xFF),
# which DataInputStream reports as EOF. (RandomAccessFile.read() in the same classlib masks correctly.)
s/return read != 0 ? buffer\[0\] : -1;/return read != 0 ? buffer[0] \& 0xFF : -1;/
