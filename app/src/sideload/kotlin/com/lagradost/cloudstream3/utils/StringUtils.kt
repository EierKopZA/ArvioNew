package com.lagradost.cloudstream3.utils

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Added in CloudStream master (post-4.4.0). Vendored into arvio's sideload
 * flavor so community plugins compiled against that newer SDK surface
 * resolve these extensions at DexClassLoader time.
 */
object StringUtils {
    fun String.encodeUri(): String {
        return URLEncoder.encode(this, "UTF-8")
    }

    fun String.decodeUri(): String {
        return URLDecoder.decode(this, "UTF-8")
    }
}
