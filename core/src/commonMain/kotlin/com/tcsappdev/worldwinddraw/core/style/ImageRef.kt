package com.tcsappdev.worldwinddraw.core.style

/**
 * Where an image comes from. `core` can't name a platform image type (a `Bitmap`, a `UIImage`...)
 * and there is no file path that works everywhere (the web and iOS have none), so an image is
 * either a URL or the raw bytes of an encoded image file.
 */
public sealed interface ImageRef {
    /** An `http`/`https` URL the device can reach; downloaded on demand and cached by URL. */
    public data class Url(val url: String) : ImageRef

    /**
     * The bytes of an encoded image file (PNG, JPEG, ...) — what an app gets from a photo picker,
     * a file, or its own assets on any platform. That makes this the way to use a picture from the
     * user's phone: on Android a picked photo is a `content://` address, not a file path, but an
     * app can always read its bytes.
     *
     * [key] identifies the *content*: the decoded image is cached under it so it isn't decoded again
     * on every redraw. Two [Bytes] with the same key are treated as the same image (equality and
     * hashing use only the key), so give a different key to different pictures, and a new key when
     * the picture changes.
     *
     * Large images are shrunk when they are decoded, so there is no need to downscale first.
     */
    public class Bytes(public val key: String, public val data: ByteArray) : ImageRef {
        init {
            require(key.isNotBlank()) { "key must not be blank" }
            require(data.isNotEmpty()) { "data must not be empty" }
        }

        override fun equals(other: Any?): Boolean = other is Bytes && other.key == key
        override fun hashCode(): Int = key.hashCode()
        override fun toString(): String = "Bytes(key=$key, ${data.size} bytes)"
    }
}
