package at.libmatti.mtztool

import java.io.ByteArrayOutputStream

/**
 * Minimal binary resources.arsc writer for generated icon packs.
 *
 * Only a single "drawable" type is required: one entry per icon pointing to
 * `res/drawable/<name>.png` (or `.webp`) via a Res_value of type TYPE_STRING.
 * This keeps the generator independent of aapt2, which is not available on the
 * device.
 */
internal object ResourceTable {

    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_TABLE = 0x0002
    private const val TYPE_PACKAGE = 0x0200
    private const val TYPE_TYPE = 0x0201
    private const val TYPE_TYPE_SPEC = 0x0202

    private const val TABLE_HEADER_SIZE = 12
    private const val PACKAGE_HEADER_SIZE = 288
    private const val TYPE_SPEC_HEADER_SIZE = 16
    private const val TYPE_HEADER_SIZE = 84
    private const val CONFIG_SIZE = 64
    private const val UTF8_FLAG = 0x100

    private const val RES_VALUE_STRING = 3

    /** A single drawable entry: resource name plus path inside the APK. */
    data class Drawable(val name: String, val path: String)

    fun build(packageName: String, drawables: List<Drawable>): ByteArray {
        require(drawables.isNotEmpty()) { "Keine Drawables übergeben" }
        require(drawables.map { it.name }.distinct().size == drawables.size) {
            "Doppelte Drawable-Namen"
        }

        val globalPool = stringPool(drawables.map { it.path })
        val typePool = stringPool(listOf("drawable"))
        val keyPool = stringPool(drawables.map { it.name })
        val spec = typeSpec(drawables.size)
        val type = typeChunk(drawables.size)
        val packageChunk = packageChunk(packageName, typePool, keyPool, spec, type)

        val size = TABLE_HEADER_SIZE + globalPool.size + packageChunk.size
        val out = ByteArrayOutputStream(size)
        writeShort(out, TYPE_TABLE)
        writeShort(out, TABLE_HEADER_SIZE)
        writeInt(out, size)
        writeInt(out, 1)         // packageCount
        out.write(globalPool)
        out.write(packageChunk)
        return out.toByteArray()
    }

    private fun typeSpec(entryCount: Int): ByteArray {
        val size = TYPE_SPEC_HEADER_SIZE + entryCount * 4
        val out = ByteArrayOutputStream(size)
        writeShort(out, TYPE_TYPE_SPEC)
        writeShort(out, TYPE_SPEC_HEADER_SIZE)
        writeInt(out, size)
        out.write(1)             // type id: drawable
        out.write(0)             // res0
        writeShort(out, 0)       // res1
        writeInt(out, entryCount)
        repeat(entryCount) { writeInt(out, 0) }
        return out.toByteArray()
    }

    private fun typeChunk(entryCount: Int): ByteArray {
        val entriesStart = TYPE_HEADER_SIZE + entryCount * 4
        val size = entriesStart + entryCount * 16
        val out = ByteArrayOutputStream(size)

        writeShort(out, TYPE_TYPE)
        writeShort(out, TYPE_HEADER_SIZE)
        writeInt(out, size)
        out.write(1)             // type id: drawable
        out.write(0)             // flags
        writeShort(out, 0)       // reserved
        writeInt(out, entryCount)
        writeInt(out, entriesStart)

        // ResTable_config, default values (only the size field is set).
        writeInt(out, CONFIG_SIZE)
        repeat(CONFIG_SIZE - 4) { out.write(0) }

        repeat(entryCount) { writeInt(out, it * 16) }

        repeat(entryCount) { index ->
            // ResTable_entry
            writeShort(out, 8)
            writeShort(out, 0)
            writeInt(out, index)         // key index
            // Res_value: TYPE_STRING -> global string pool index
            writeShort(out, 8)
            out.write(0)                 // res0
            out.write(RES_VALUE_STRING)  // dataType
            writeInt(out, index)         // data (path index)
        }
        return out.toByteArray()
    }

    private fun packageChunk(
        packageName: String,
        typePool: ByteArray,
        keyPool: ByteArray,
        spec: ByteArray,
        type: ByteArray,
    ): ByteArray {
        val keyStringsOffset = PACKAGE_HEADER_SIZE + typePool.size
        val size = keyStringsOffset + keyPool.size + spec.size + type.size
        val out = ByteArrayOutputStream(size)

        writeShort(out, TYPE_PACKAGE)
        writeShort(out, PACKAGE_HEADER_SIZE)
        writeInt(out, size)
        writeInt(out, 0x7f)          // package id

        // Package name as 128 UTF-16 code units.
        val name = packageName.take(127)
        repeat(128) { i ->
            val c = if (i < name.length) name[i].code else 0
            out.write(c and 0xff)
            out.write((c shr 8) and 0xff)
        }

        writeInt(out, PACKAGE_HEADER_SIZE)  // typeStrings
        writeInt(out, 0)                    // lastPublicType
        writeInt(out, keyStringsOffset)     // keyStrings
        writeInt(out, 0)                    // lastPublicKey
        writeInt(out, 0)                    // typeIdOffset

        out.write(typePool)
        out.write(keyPool)
        out.write(spec)
        out.write(type)
        return out.toByteArray()
    }

    private fun stringPool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for ((index, value) in strings.withIndex()) {
            offsets[index] = data.size()
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeUleb128(data, value.length)   // UTF-16 code units
            writeUleb128(data, bytes.size)
            data.write(bytes)
            data.write(0)
        }
        while (data.size() % 4 != 0) data.write(0)

        val stringsStart = 28 + 4 * strings.size
        val size = stringsStart + data.size()
        val out = ByteArrayOutputStream(size)
        writeShort(out, TYPE_STRING_POOL)
        writeShort(out, 28)
        writeInt(out, size)
        writeInt(out, strings.size)
        writeInt(out, 0)              // styleCount
        writeInt(out, UTF8_FLAG)
        writeInt(out, stringsStart)
        writeInt(out, 0)              // stylesStart
        offsets.forEach { writeInt(out, it) }
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    private fun writeUleb128(out: ByteArrayOutputStream, value: Int) {
        var v = value
        do {
            var b = v and 0x7f
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value shr 8) and 0xff)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value shr 8) and 0xff)
        out.write((value shr 16) and 0xff)
        out.write((value shr 24) and 0xff)
    }
}
