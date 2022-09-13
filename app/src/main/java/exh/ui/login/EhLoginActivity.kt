package exh.ui.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.presentation.webview.EhLoginWebViewScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import exh.log.xLogD
import uy.kohesive.injekt.injectLazy
import java.net.HttpCookie
import java.util.Locale

/**
 * LoginController
 */
class EhLoginActivity : BaseActivity() {
    private val preferenceManager: PreferencesHelper by injectLazy()

    private var igneous: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!WebViewUtil.supportsWebView(this)) {
            toast(R.string.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        setComposeContent {
            EhLoginWebViewScreen(
                onUp = { finish() },
                onPageFinished = ::onPageFinished,
                onClickRecheckLoginStatus = ::recheckLoginStatus,
                onClickAlternateLoginPage = ::alternateLoginPage,
                onClickSkipPageRestyling = ::skipPageRestyling,
                onClickCustomIgneousCookie = ::openIgneousDialog,
            )
        }
    }

    private fun recheckLoginStatus(loadUrl: (String) -> Unit) {
        loadUrl("https://exhentai.org/")
    }

    private fun alternateLoginPage(loadUrl: (String) -> Unit) {
        loadUrl("https://e-hentai.org/bounce_login.php")
    }

    private fun skipPageRestyling(loadUrl: (String) -> Unit) {
        loadUrl("https://forums.e-hentai.org/index.php?act=Login&$PARAM_SKIP_INJECT=true")
    }

    private fun openIgneousDialog() {
        var igneous: CharSequence? = null
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_igneous_cookie)
            .setMessage(R.string.custom_igneous_cookie_message)
            .setTextInput { igneousText ->
                igneous = igneousText
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (!igneous.isNullOrBlank()) {
                    this.igneous = igneous?.toString()?.trim()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onPageFinished(view: WebView, url: String) {
        xLogD(url)
        val parsedUrl = Uri.parse(url)
        if (parsedUrl.host.equals("forums.e-hentai.org", ignoreCase = true)) {
            // Hide distracting content
            if (!parsedUrl.queryParameterNames.contains(PARAM_SKIP_INJECT)) {
                view.evaluateJavascript(HIDE_JS, null)
            }
            // Check login result

            if (parsedUrl.getQueryParameter("code")?.toInt() != 0) {
                if (checkLoginCookies(url)) view.loadUrl("https://exhentai.org/")
            }
        } else if (parsedUrl.host.equals("exhentai.org", ignoreCase = true)) {
            // At ExHentai, check that everything worked out...
            if (applyExHentaiCookies(url)) {
                preferenceManager.enableExhentai().set(true)
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    /**
     * Check if we are logged in
     */
    private fun checkLoginCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->
            return parsed.count {
                (
                    it.name.equals(MEMBER_ID_COOKIE, ignoreCase = true) ||
                        it.name.equals(PASS_HASH_COOKIE, ignoreCase = true)
                    ) &&
                    it.value.isNotBlank()
            } >= 2
        }
        return false
    }

    /**
     * Parse cookies at ExHentai
     */
    private fun applyExHentaiCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->

            var memberId: String? = null
            var passHash: String? = null
            var igneous: String? = null

            parsed.forEach {
                when (it.name.lowercase(Locale.getDefault())) {
                    MEMBER_ID_COOKIE -> memberId = it.value
                    PASS_HASH_COOKIE -> passHash = it.value
                    IGNEOUS_COOKIE -> igneous = this.igneous ?: it.value
                }
            }

            // Missing a cookie
            if (memberId == null || passHash == null || igneous == null) return false

            // Update prefs
            preferenceManager.memberIdVal().set(memberId!!)
            preferenceManager.passHashVal().set(passHash!!)
            preferenceManager.igneousVal().set(igneous!!)

            return true
        }
        return false
    }

    private fun getCookies(url: String): List<HttpCookie>? =
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
            cookie.split("; ").flatMap {
                HttpCookie.parse(it)
            }
        }

    companion object {
        const val PARAM_SKIP_INJECT = "TEH_SKIP_INJECT"

        const val MEMBER_ID_COOKIE = "ipb_member_id"
        const val PASS_HASH_COOKIE = "ipb_pass_hash"
        const val IGNEOUS_COOKIE = "igneous"

        const val HIDE_JS =
            """
                    javascript:(function () {
                        document.getElementsByTagName('body')[0].style.visibility = 'hidden';
                        document.getElementsByName('submit')[0].style.visibility = 'visible';
                        document.querySelector('td[width="60%"][valign="top"]').style.visibility = 'visible';

                        function hide(e) {if(e != null) e.style.display = 'none';}

                        hide(document.querySelector(".errorwrap"));
                        hide(document.querySelector('td[width="40%"][valign="top"]'));
                        var child = document.querySelector(".page").querySelector('div');
                        child.style.padding = null;
                        var ft = child.querySelectorAll('table');
                        var fd = child.parentNode.querySelectorAll('div > div');
                        var fh = document.querySelector('#border').querySelectorAll('td > table');
                        hide(ft[0]);
                        hide(ft[1]);
                        hide(fd[1]);
                        hide(fd[2]);
                        hide(child.querySelector('br'));
                        var error = document.querySelector(".page > div > .borderwrap");
                        if(error != null) error.style.visibility = 'visible';
                        hide(fh[0]);
                        hide(fh[1]);
                        hide(document.querySelector("#gfooter"));
                        hide(document.querySelector(".copyright"));
                        document.querySelectorAll("td").forEach(function(e) {
                            e.style.color = "white";
                        });
                        var pc = document.querySelector(".postcolor");
                        if(pc != null) pc.style.color = "#26353F";
                    })()
                    """

        fun newIntent(context: Context): Intent {
            return Intent(context, EhLoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
