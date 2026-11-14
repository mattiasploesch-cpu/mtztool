package at.libmatti.mtztool

import android.util.Log
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Offline replica of miui.drm.DrmManager.isRightsFileLegal() and the asset UID
 * parser. A .mra is considered valid only if the RSA-1024 signature in the
 * root attribute `o-ex:id` verifies against the hardcoded Xiaomi public key.
 * Self generated .mra files can never pass this check.
 */
object MraVerifier {

    private const val TAG = "MraVerifier"

    private const val MODULUS =
        "a2ebd07cfae9a72345fc3c95d80cf5a21a55bf553fbab3025c82747ba4d53d1f9b02f46c20b5520585a910732698b165f0ecf7bd9ce5402e27c646cd0c5d34cff92b184d6a477e156a7d3503b756cc3e8531fb26c0da0ca051ab531c7f9f2a040a06642cadb698882c048630030b73edbbd62da73f7027065443c6e2558edfbd"
    private const val EXPONENT = "10001"

    data class Rights(
        val signatureValid: Boolean,
        val themeHash: String?,
        val assets: Set<String>,
        val error: String? = null
    )

    private val publicKey: PublicKey by lazy {
        KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(MODULUS, 16), BigInteger(EXPONENT, 16))
        )
    }

    fun verify(bytes: ByteArray): Rights {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val root = doc.documentElement

            // Namespace-unaware lookup, exactly like DrmManager.
            val sigHex = root.getAttribute("o-ex:id")
            if (sigHex.length != 256 || !sigHex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                return Rights(false, null, emptySet(), "keine gültige Signatur im Attribut o-ex:id")
            }

            val nodes = root.childNodes
            val content = buildString {
                for (i in 0 until nodes.length) {
                    append(nodeToString(nodes.item(i)))
                }
            }

            val sig = hexToBytes(sigHex)
            val okStandard = verifySignature(content.toByteArray(Charsets.UTF_8), sig)
            val okOldFormat = !okStandard && verifySignature(
                content.replace("/>", " />").toByteArray(Charsets.UTF_8),
                sig
            )
            val valid = okStandard || okOldFormat

            val uid = findAssetUid(doc)
            val themeHash = uid?.substringBefore(':')?.trim()?.takeIf { it.length == 40 }
            val assets = uid
                ?.substringAfter(':', "")
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.filter { it.length == 40 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
                ?.toSet()
                ?: emptySet()

            if (!valid) {
                return Rights(false, themeHash, assets, "RSA-Signatur ungültig")
            }
            Rights(true, themeHash, assets, null)
        } catch (e: Exception) {
            Log.w(TAG, "MRA verification failed", e)
            Rights(false, null, emptySet(), e.message ?: "unlesbare .mra")
        }
    }

    /** Serializes a single DOM node the same way the framework does. */
    private fun nodeToString(node: Node): String {
        val writer = StringWriter()
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.transform(DOMSource(node), StreamResult(writer))
        return writer.toString()
    }

    private fun verifySignature(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            Log.w(TAG, "Signature check failed", e)
            false
        }
    }

    /**
     * Finds the asset UID (`<themeHash>:<sha1>,<sha1>...`). The timestamp UID
     * in the outer context does not contain a colon, so it is skipped.
     */
    private fun findAssetUid(doc: Document): String? {
        val nodes = doc.getElementsByTagName("o-dd:uid")
        for (i in 0 until nodes.length) {
            val value = nodes.item(i).textContent?.trim().orEmpty()
            if (value.contains(':') && value.substringAfter(':').isNotBlank()) {
                return value
            }
        }
        // Fallback for documents that use a different namespace prefix.
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val node = all.item(i)
            if (!node.nodeName.endsWith(":uid") && node.nodeName != "uid") continue
            val value = node.textContent?.trim().orEmpty()
            if (value.contains(':') && value.substringAfter(':').isNotBlank()) {
                return value
            }
        }
        return null
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }
}
