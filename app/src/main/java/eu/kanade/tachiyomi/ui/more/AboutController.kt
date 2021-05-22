package eu.kanade.tachiyomi.ui.more

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.aboutlibraries.LibsBuilder
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.UpdateResult
import eu.kanade.tachiyomi.data.updater.UpdaterService
import eu.kanade.tachiyomi.data.updater.github.GithubUpdateChecker
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.ui.main.WhatsNewDialogController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.preference.add
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import exh.syDebugVersion
import timber.log.Timber
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AboutController : SettingsController() {

    private val updateChecker by lazy { GithubUpdateChecker() }

    private val dateFormat: DateFormat = preferences.dateFormat()

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_about

        add(AboutLinksPreference(context))

        preferenceCategory {
            preference {
                key = "pref_about_version"
                titleRes = R.string.version
                summary = if (BuildConfig.DEBUG /* SY --> */ || syDebugVersion != "0" /* SY --> */) {
                    "Preview r$syDebugVersion (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                } else {
                    "Stable ${BuildConfig.VERSION_NAME} (${getFormattedBuildTime()})"
                }

                onClick { copyDebugInfo() }
            }
            if (isUpdaterEnabled) {
                preference {
                    key = "pref_about_check_for_updates"
                    titleRes = R.string.check_for_updates

                    onClick { checkVersion() }
                }
            }
            preference {
                key = "pref_about_whats_new"
                titleRes = R.string.whats_new

                onClick {
                    // SY -->
                    WhatsNewDialogController().showDialog(router)
                    // SY <--
                }
            }
            preference {
                key = "pref_about_licenses"
                titleRes = R.string.licenses
                onClick {
                    LibsBuilder()
                        .withActivityTitle(activity!!.getString(R.string.licenses))
                        .withAboutIconShown(false)
                        .withAboutVersionShown(false)
                        .withLicenseShown(true)
                        .withEdgeToEdge(true)
                        .start(activity!!)
                }
            }
        }
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private fun checkVersion() {
        if (activity == null) return

        activity?.toast(R.string.update_check_look_for_updates)

        launchNow {
            try {
                when (val result = updateChecker.checkForUpdate()) {
                    is UpdateResult.NewUpdate<*> -> {
                        val body = result.release.info
                        val url = result.release.downloadLink

                        // Create confirmation window
                        NewUpdateDialogController(body, url).showDialog(router)
                    }
                    is UpdateResult.NoNewUpdate -> {
                        activity?.toast(R.string.update_check_no_new_updates)
                    }
                }
            } catch (error: Exception) {
                activity?.toast(error.message)
                Timber.e(error)
            }
        }
    }

    class NewUpdateDialogController(bundle: Bundle? = null) : DialogController(bundle) {

        constructor(body: String, url: String) : this(
            bundleOf(BODY_KEY to body, URL_KEY to url)
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                .title(res = R.string.update_check_notification_update_available)
                .message(text = args.getString(BODY_KEY) ?: "")
                .positiveButton(R.string.update_check_confirm) {
                    val appContext = applicationContext
                    if (appContext != null) {
                        // Start download
                        val url = args.getString(URL_KEY) ?: ""
                        UpdaterService.start(appContext, url)
                    }
                }
                .negativeButton(R.string.update_check_ignore)
        }

        private companion object {
            const val BODY_KEY = "NewUpdateDialogController.body"
            const val URL_KEY = "NewUpdateDialogController.key"
        }
    }

    private fun copyDebugInfo() {
        activity?.let {
            val deviceInfo = CrashLogUtil(it).getDebugInfo()
            activity?.copyToClipboard("Debug information", deviceInfo)
        }
    }

    private fun getFormattedBuildTime(): String {
        return try {
            val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
            inputDf.timeZone = TimeZone.getTimeZone("UTC")
            val buildTime = inputDf.parse(BuildConfig.BUILD_TIME)

            val outputDf = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            )
            outputDf.timeZone = TimeZone.getDefault()

            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            buildTime.toDateTimestampString(dateFormat)
        } catch (e: ParseException) {
            BuildConfig.BUILD_TIME
        }
    }
}

private class AboutLinksPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_about_links
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // holder.findViewById(R.id.btn_website).setOnClickListener { openInBrowser("https://tachiyomi.org") }
        // holder.findViewById(R.id.btn_discord).setOnClickListener { openInBrowser("https://discord.gg/tachiyomi") }
        // holder.findViewById(R.id.btn_twitter).setOnClickListener { openInBrowser("https://twitter.com/tachiyomiorg") }
        // holder.findViewById(R.id.btn_facebook).setOnClickListener { openInBrowser("https://facebook.com/tachiyomiorg") }
        // holder.findViewById(R.id.btn_github).setOnClickListener { openInBrowser("https://github.com/tachiyomiorg") }
    }
}
