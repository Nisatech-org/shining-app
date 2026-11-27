package app.shining.credit.domain

import android.net.Uri

/** The Samad page for topping up credit, opened already logged in with [loginAsToken]. */
fun creditChargeUrl(loginAsToken: String): String =
    Uri.Builder()
        .scheme("https")
        .authority("setad.dining.sharif.edu")
        .appendPath("j_security_check")
        .appendQueryParameter("loginAsToken", loginAsToken)
        .appendQueryParameter("redirect", "/nurture/user/credit/charge/view.rose")
        .build()
        .toString()
