package io.github.dueris.kotlin.eclipse.gradle

import net.fabricmc.accesswidener.AccessWidenerFormatException
import net.fabricmc.accesswidener.AccessWidenerReader
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader

class Verification {
    @JvmOverloads
    fun read(content: ByteArray?, currentNamespace: String? = null) {
        val strContent = String(content!!, AccessWidenerReader.ENCODING)

        try {
            read(BufferedReader(StringReader(strContent)), currentNamespace)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun read(reader: BufferedReader, currentNamespace: String? = null) {
        val header = readHeader(reader)
    }

    @Throws(IOException::class)
    fun readHeader(reader: BufferedReader) {
        val headerLine = reader.readLine()
        val header = headerLine.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (header.size != 3 || header[0] != "accessWidener") {
            throw AccessWidenerFormatException(
                1,
                "Invalid access widener file header. Expected: 'accessWidener <version> <namespace>'"
            )
        }
    }
}

