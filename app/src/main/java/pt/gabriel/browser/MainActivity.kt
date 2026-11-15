package pt.gabriel.browser

import android.app.Activity
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse

private data class Engine(val name: String, val desc: String, val home: String, val query: String)

private val ENGINES = listOf(
    Engine("DuckDuckGo Lite", "Versão leve e sem distrações, mantém o anonimato",
        "https://lite.duckduckgo.com/lite/", "https://lite.duckduckgo.com/lite/?q="),
    Engine("DuckDuckGo", "Pesquisa privada que não te rastreia",
        "https://duckduckgo.com/", "https://duckduckgo.com/?q="),
    Engine("Brave Search", "Índice próprio e independente, focado na privacidade",
        "https://search.brave.com/", "https://search.brave.com/search?q="),
    Engine("Startpage", "Resultados do Google de forma anónima",
        "https://www.startpage.com/", "https://www.startpage.com/do/search?q="),
    Engine("Google", "O mais usado; recolhe dados das pesquisas",
        "https://www.google.com/", "https://www.google.com/search?q="),
    Engine("GIBIRU", "Resultados baseados no Google, sem rastreio",
        "https://gibiru.com/", "https://gibiru.com/results.html?q="),
    Engine("Bing", "Resultados detalhados da Microsoft",
        "https://www.bing.com/", "https://www.bing.com/search?q="),
    Engine("Swisscows", "Pesquisa suíça, boa para música (SoundCloud); exclui conteúdo malicioso e adulto",
        "https://swisscows.com/", "https://swisscows.com/web?query=")
)

// Parâmetros de rastreio removidos ao partilhar/copiar links.
private val TRACKING_PARAMS = setOf(
    "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "igshid", "mc_eid", "mc_cid",
    "yclid", "_hsenc", "_hsmi", "ref_src", "ref_url", "spm", "vero_id", "oly_enc_id", "oly_anon_id"
)

/**
 * Gabriel Browser: browser leve sobre GeckoView (motor do Firefox).
 *
 *  - DNS sobre HTTPS (DoH) só para a navegação da app (predefinido: Control D p2)
 *  - Proteção contra rastreio ESTRITA, só HTTPS e Safe Browsing
 *  - uBlock Origin embutido (pasta assets/ublock)
 *  - Bloqueio de pop-ups e de redirecionamentos por script sem clique
 *  - Downloads, envio de ficheiros, ecrã inteiro, recuperação de crash
 *  - Painel de proteção, limpar dados do site, links sem parâmetros de rastreio
 *
 * NOTA: os nomes da API do GeckoView mudam entre versões. Se o Android Studio
 * marcar algum método a vermelho, consulta o Javadoc da versão usada:
 * https://mozilla.github.io/geckoview/javadoc/mozilla-central/
 */
class MainActivity : Activity() {

    companion object {
        const val DEFAULT_DOH = "https://freedns.controld.com/p2"
        private const val PREFS = "gabriel"
        private const val KEY_DOH = "doh_url"
        private const val KEY_ENGINE = "engine"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HOME = "home_url"
        private const val KEY_LANG = "lang"
        private const val KEY_VT_KEY = "vt_key"
        private const val KEY_HISTORY = "history"
        private const val KEY_HISTORY_ON = "history_on"
        private const val HISTORY_MAX = 300
        private const val MAX_TABS = 12
        private const val MEDIA_ID = "media@gabriel.browser"
        private const val MEDIA_MAX = 150
        private const val KEY_VT_AUTO = "vt_auto"
        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
        private const val REQ_FILE = 42
        private const val REQ_FOLDER = 43
        private const val REQ_PERM = 44
        private const val KEY_DL_TREE = "dl_tree"
        private const val TAG = "GabrielBrowser"
        private const val BROWSER_GUARD_URL =
            "https://addons.mozilla.org/firefox/downloads/file/5140538/malwarebytes-3.3.8.xpi"

        // O GeckoRuntime só pode ser criado uma vez por processo.
        private var runtime: GeckoRuntime? = null

        @Volatile
        private var ublockOn = false

        // Extensão própria que deteta media na página (ver assets/media).
        @Volatile
        private var mediaExt: WebExtension? = null
    }

    private lateinit var rt: GeckoRuntime
    // Cada separador tem a sua sessão do motor e o seu estado.
    private class Tab(var session: GeckoSession) {
        var url: String? = null
        var title: String? = null
        var canGoBack = false
        var canGoForward = false
        var loading = false
        var progress = 0
        var blocked = 0
        var desktop = false
        var appNav = false
        var lastInsecure: String? = null
        val media = LinkedHashMap<String, MediaItem>()
    }

    private data class MediaItem(val url: String, val kind: String, val w: Int, val h: Int)

    private val tabs = mutableListOf<Tab>()
    private lateinit var active: Tab
    private val session: GeckoSession get() = active.session

    private lateinit var geckoView: GeckoView
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var divider: View
    private lateinit var urlBar: EditText
    private lateinit var siteIcon: ImageView
    private lateinit var btnClear: ImageButton
    private lateinit var shieldBox: LinearLayout
    private lateinit var shieldCount: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnTabs: View
    private lateinit var mediaBtn: TextView
    private lateinit var tabCount: TextView

    // O estado do separador ativo, para o resto do código.
    private var currentUrl: String?
        get() = active.url
        set(v) { active.url = v }
    private var canGoBack: Boolean
        get() = active.canGoBack
        set(v) { active.canGoBack = v }
    private var canGoForward: Boolean
        get() = active.canGoForward
        set(v) { active.canGoForward = v }
    private var loading: Boolean
        get() = active.loading
        set(v) { active.loading = v }
    private var blockedCount: Int
        get() = active.blocked
        set(v) { active.blocked = v }
    private var desktopMode: Boolean
        get() = active.desktop
        set(v) { active.desktop = v }
    private var fullscreen = false

    // true quando fomos nós (app) a iniciar a navegação (carregar, voltar, avançar, recarregar)
    private var appNav: Boolean
        get() = active.appNav
        set(v) { active.appNav = v }

    private var pendingFile: Pair<
        GeckoSession.PromptDelegate.FilePrompt,
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse>
        >? = null

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.setup(prefs().getString(KEY_LANG, "auto"))
        setContentView(R.layout.activity_main)
        bindViews()

        rt = getOrCreateRuntime()
        openNewTab(null, load = false)
        setupUi()
        updateNavButtons()
        updateShield()

        val f = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dlReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(dlReceiver, f)

        appLoad(safeIntentUrl(intent) ?: homeUrl())
    }

    // Só aceita http(s) vindo de outras apps (ignora javascript:, file:, about:, intent: …).
    private fun safeIntentUrl(i: Intent?): String? {
        val d = i?.dataString ?: return null
        return if (d.startsWith("https://") || d.startsWith("http://")) d else null
    }

    override fun onDestroy() {
        try { unregisterReceiver(dlReceiver) } catch (e: Exception) { }
        // Sem isto, as sessões ficavam vivas no processo depois de sair da app (memória e dados em modo privado).
        try { geckoView.releaseSession() } catch (e: Exception) { Log.w(TAG, "releaseSession: $e") }
        tabs.forEach { try { it.session.close() } catch (e: Exception) { Log.w(TAG, "close: $e") } }
        tabs.clear()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        safeIntentUrl(intent)?.let { openNewTab(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullscreen -> session.exitFullScreen()
            urlBar.hasFocus() -> {
                urlBar.clearFocus()
                geckoView.requestFocus()
            }
            canGoBack -> appBack()
            tabs.size > 1 -> closeTab(active)
            else -> super.onBackPressed()
        }
    }

    // ---------------------------------------------------------------- motor

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    // Página inicial: a definida pelo utilizador ou, se vazia, a do motor de pesquisa.
    private fun homeUrl(): String =
        prefs().getString(KEY_HOME, "")?.trim().orEmpty().ifEmpty { engine().home }

    private fun showLanguage() {
        val saved = prefs().getString(KEY_LANG, "auto") ?: "auto"
        val sh = Sheet(this, "Idioma", "Escolhe o idioma da app")
        val options = listOf("auto" to "Automático (idioma do sistema)") + I18n.LANGS
        options.forEach { (code, name) ->
            sh.card(
                icon = if (code == saved) "✓" else "🌍",
                title = name,
                onClick = {
                    prefs().edit().putString(KEY_LANG, code).apply()
                    I18n.setup(code)
                    urlBar.hint = I18n.tr(
                        if (privateMode) "🕶 Privado — pesquisar ou escrever endereço" else "Pesquisar ou escrever endereço"
                    )
                }
            )
        }
        sh.actions(Triple("Fechar", BtnStyle.SECONDARY) {}).show()
    }

    private fun showHomeSettings() {
        val sh = Sheet(this, "Página inicial", "Aberta ao iniciar a app e no botão Início")
        val input = sh.input("ex.: https://exemplo.pt  (vazio = página do motor)", prefs().getString(KEY_HOME, "").orEmpty())
        sh.actions(
            Triple("Guardar", BtnStyle.PRIMARY) {
                var v = input.text.toString().trim()
                if (v.isNotEmpty() && !v.startsWith("http://") && !v.startsWith("https://") && !v.startsWith("about:")) {
                    v = "https://$v"
                }
                prefs().edit().putString(KEY_HOME, v).apply()
                toast("Página inicial guardada")
            },
            Triple("Usar a página atual", BtnStyle.SECONDARY) {
                val u = currentUrl
                if (u != null && (u.startsWith("https://") || u.startsWith("http://"))) {
                    prefs().edit().putString(KEY_HOME, u).apply()
                    toast("Página inicial definida")
                } else toast("Abre primeiro uma página")
            },
            Triple("Cancelar", BtnStyle.SECONDARY) {}
        ).show()
    }

    private fun engine(): Engine = ENGINES[prefs().getInt(KEY_ENGINE, 0).coerceIn(ENGINES.indices)]

    private fun getOrCreateRuntime(): GeckoRuntime {
        runtime?.let { return it }

        val dohUrl = prefs().getString(KEY_DOH, DEFAULT_DOH)?.trim().orEmpty()

        val builder = GeckoRuntimeSettings.Builder()
            // Só HTTPS
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            // Permite afinar preferências em about:config (opcional)
            .aboutConfigEnabled(true)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .build()
            )

        if (dohUrl.isNotEmpty()) {
            // TRR_MODE_ONLY: todos os pedidos DNS da app vão por DoH, sem recuar para o DNS do sistema.
            builder.trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_ONLY)
                .trustedRecursiveResolverUri(dohUrl)
        }

        val created = GeckoRuntime.create(applicationContext, builder.build())
        runtime = created

        // Confirmação de permissões ao instalar extensões
        created.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                runOnUiThread {
                    val name = extension.metaData.name ?: "Extensão"
                    var answered = false
                    fun answer(ok: Boolean) {
                        if (!answered) {
                            answered = true
                            result.complete(WebExtension.PermissionPromptResponse(ok, false, false))
                        }
                    }
                    val sh = Sheet(this@MainActivity, "Adicionar \"$name\"?", "Revê as permissões pedidas")
                    if (permissions.isEmpty() && origins.isEmpty()) sh.text("Não pede permissões especiais.")
                    if (permissions.isNotEmpty()) {
                        sh.section("Permissões")
                        permissions.forEach { sh.info("🔑", it, "") }
                    }
                    if (origins.isNotEmpty()) {
                        sh.section("Sites a que acede")
                        origins.forEach { sh.info("🌐", it, "") }
                    }
                    sh.actions(
                        Triple("Cancelar", BtnStyle.SECONDARY) { answer(false) },
                        Triple("Adicionar", BtnStyle.PRIMARY) { answer(true) }
                    )
                    sh.dialog.setOnCancelListener { answer(false) }
                    sh.show()
                }
                return result
            }
        }

        // uBlock Origin embutido (se a pasta assets/ublock tiver os ficheiros da extensão)
        created.webExtensionController
            .ensureBuiltIn("resource://android/assets/ublock/", UBLOCK_ID)
            .accept(
                {
                    ublockOn = true
                    Log.i(TAG, "uBlock Origin ativo: ${it?.id}")
                },
                { Log.w(TAG, "uBlock Origin não carregou (ver assets/ublock/LEIAME.txt): $it") }
            )

        // Detetor de media (vídeo, áudio, imagens), extensão própria embutida
        created.webExtensionController
            .ensureBuiltIn("resource://android/assets/media/", MEDIA_ID)
            .accept(
                { ext ->
                    mediaExt = ext
                    Log.i(TAG, "Detetor de media ativo: ${ext?.id}")
                    runOnUiThread { tabs.forEach { attachMediaDelegate(it.session) } }
                },
                { Log.w(TAG, "Detetor de media não carregou: $it") }
            )
        return created
    }

    // Modo privado: sessão sem histórico/cookies persistentes; não vai para os recentes nem permite capturas de ecrã.
    private var privateMode = false

    private fun togglePrivateMode() {
        privateMode = !privateMode
        if (privateMode) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        val old = tabs.toList()
        tabs.clear()
        old.forEach {
            try {
                it.session.close()
            } catch (e: Exception) {
                Log.w(TAG, "close falhou: $e")
            }
        }
        openNewTab(null)
        urlBar.hint = I18n.tr(if (privateMode) "🕶 Privado — pesquisar ou escrever endereço" else "Pesquisar ou escrever endereço")
        toast(if (privateMode) "Modo privado ligado" else "Modo privado desligado")
    }

    // ---------------------------------------------------------------- deteção de media

    private val mediaDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            val sess = sender.session ?: return null
            val tab = tabs.firstOrNull { it.session === sess } ?: return null
            try {
                val o = message as? org.json.JSONObject ?: return null
                if (o.optString("type") != "media") return null
                val arr = o.optJSONArray("items") ?: return null
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val url = it.optString("url")
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) continue
                    if (tab.media.size >= MEDIA_MAX) break
                    if (!tab.media.containsKey(url)) {
                        tab.media[url] = MediaItem(url, it.optString("kind", "image"), it.optInt("w"), it.optInt("h"))
                    }
                }
                runOnUiThread { if (tab === active) updateMediaButton() }
            } catch (e: Exception) {
                Log.w(TAG, "mensagem de media inválida: $e")
            }
            return null
        }
    }

    private fun attachMediaDelegate(s: GeckoSession) {
        val ext = mediaExt ?: return
        try {
            s.webExtensionController.setMessageDelegate(ext, mediaDelegate, "browser")
        } catch (e: Exception) {
            Log.w(TAG, "setMessageDelegate falhou: $e")
        }
    }

    private fun updateMediaButton() {
        val n = active.media.size
        mediaBtn.visibility = if (n > 0) View.VISIBLE else View.GONE
        mediaBtn.text = "⬇ $n"
    }

    private fun mediaName(url: String): String {
        val last = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
        return last ?: (hostOf(url) ?: url)
    }

    private fun showMedia() {
        val items = active.media.values.toList()
        val order = mapOf("video" to 0, "audio" to 1, "image" to 2)
        val sorted = items.sortedWith(
            compareBy<MediaItem> { order[it.kind] ?: 3 }.thenByDescending { it.w * it.h }
        )
        val n = sorted.size
        val sh = Sheet(this, "Media desta página", if (n == 1) "1 ficheiro" else "$n ficheiros")
        if (sorted.isEmpty()) sh.text("Sem media detetada nesta página.")
        sorted.take(60).forEach { m ->
            val icon = when (m.kind) {
                "video" -> "🎬"
                "audio" -> "🎵"
                else -> "🖼"
            }
            val size = if (m.w > 0 && m.h > 0) "${m.w}×${m.h} · " else ""
            sh.card(
                icon = icon,
                title = mediaName(m.url),
                detail = size + (hostOf(m.url) ?: ""),
                onClick = { downloadUrl(m.url, null, null, true) },
                actions = listOf(Pair("🔗", { copyText(m.url) }))
            )
        }
        if (sorted.size > 60) sh.note("A mostrar os 60 primeiros.")
        sh.note("Toca num ficheiro para o guardar em Transferências. Vídeos em blocos ou protegidos (YouTube, streaming) não aparecem.")
        sh.actions(Triple("Fechar", BtnStyle.PRIMARY) {}).show()
    }

    // ---------------------------------------------------------------- separadores

    private fun newGeckoSession(): GeckoSession =
        GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(privateMode).build())

    private fun openNewTab(url: String?, load: Boolean = true) {
        if (tabs.size >= MAX_TABS) {
            toast("Limite de separadores atingido")
            return
        }
        val s = newGeckoSession()
        val tab = Tab(s)
        tabs.add(tab)
        setupDelegates(s, tab)
        s.open(rt)
        switchTo(tab)
        if (load) appLoad(url ?: homeUrl())
    }

    private fun attachToView(s: GeckoSession) {
        try {
            geckoView.releaseSession()
        } catch (e: Exception) {
            Log.w(TAG, "releaseSession: $e")
        }
        geckoView.setSession(s)
    }

    private fun switchTo(tab: Tab) {
        active = tab
        attachToView(tab.session)
        refreshAddressBar()
        updateNavButtons()
        updateShield()
        setLoadingUi(tab.loading)
        progress.progress = tab.progress
        progress.visibility = if (tab.loading) View.VISIBLE else View.INVISIBLE
        updateTabCount()
        updateMediaButton()
        urlBar.clearFocus()
    }

    private fun closeTab(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tabs.removeAt(idx)
        if (tab === active) {
            try { geckoView.releaseSession() } catch (e: Exception) { Log.w(TAG, "releaseSession: $e") }
        }
        try {
            tab.session.close()
        } catch (e: Exception) {
            Log.w(TAG, "close falhou: $e")
        }
        when {
            tabs.isEmpty() -> openNewTab(null)
            tab === active -> switchTo(tabs[minOf(idx, tabs.size - 1)])
            else -> updateTabCount()
        }
    }

    private fun updateTabCount() {
        runOnUiThread { tabCount.text = if (tabs.size > 99) "99+" else tabs.size.toString() }
    }

    private fun showTabs() {
        val n = tabs.size
        val sh = Sheet(this, "Separadores", if (n == 1) "1 separador" else "$n separadores")
        tabs.forEach { t ->
            val name = t.title?.trim().takeUnless { it.isNullOrEmpty() } ?: (hostOf(t.url) ?: "Novo separador")
            sh.card(
                icon = if (t === active) "●" else (name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"),
                title = name,
                detail = t.url ?: "",
                onClick = { switchTo(t) },
                actions = listOf(Pair("✕", {
                    closeTab(t)
                    showTabs()
                }))
            )
        }
        sh.actions(
            Triple("Novo separador", BtnStyle.PRIMARY) { openNewTab(null) },
            Triple("Fechar", BtnStyle.SECONDARY) {}
        ).show()
    }

    private var lastRecover = 0L
    private var recoverCount = 0

    private fun recoverSession(tab: Tab) {
        runOnUiThread {
            if (!tabs.contains(tab)) return@runOnUiThread
            // Evita ciclos: depois de 3 falhas seguidas num minuto, abre uma página em branco.
            val now = SystemClock.elapsedRealtime()
            if (now - lastRecover > 60_000) recoverCount = 0
            lastRecover = now
            recoverCount++
            val url = if (recoverCount > 3) "about:blank" else (tab.url ?: homeUrl())
            if (recoverCount > 3) toast("A página continua a falhar")
            try {
                tab.session.close()
            } catch (e: Exception) {
                Log.w(TAG, "close falhou: $e")
            }
            val s = newGeckoSession()
            tab.session = s
            setupDelegates(s, tab)
            s.open(rt)
            if (tab.desktop) applyDesktopMode(s, true)
            if (tab === active) attachToView(s)
            tab.appNav = true
            s.loadUri(url)
            toast("Página recuperada após falha")
        }
    }

    private fun applyDesktopMode(s: GeckoSession, desktop: Boolean = desktopMode) {
        s.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        s.settings.viewportMode =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }

    // ---------------------------------------------------------------- navegação iniciada pela app

    private fun appLoad(uri: String) {
        appNav = true
        session.loadUri(uri)
    }

    // Aviso de ligação não segura (http://), uma vez por endereço, com proposta de análise no VirusTotal.
    private fun warnInsecure(url: String) {
        toast("Este site não tem ligação segura")
        if (vtAuto() && vtKey().isNotEmpty()) {
            runVirusTotal(url, quiet = true)
            return
        }
        Sheet(this, "Ligação não segura", hostOf(url))
            .text("Os dados enviados para este site podem ser lidos por terceiros. Não introduzas palavras-passe nem dados pessoais.")
            .note("O VirusTotal analisa o endereço com dezenas de motores de segurança.")
            .actions(
                Triple("Analisar no VirusTotal", BtnStyle.PRIMARY) { runVirusTotal(url, quiet = false) },
                Triple("Continuar", BtnStyle.SECONDARY) {}
            )
            .show()
    }

    private fun vtKey() = prefs().getString(KEY_VT_KEY, "").orEmpty().trim()
    private fun vtAuto() = prefs().getBoolean(KEY_VT_AUTO, false)

    /** Com chave: análise automática pela API. Sem chave: copia o link e abre o VirusTotal. */
    private fun runVirusTotal(url: String, quiet: Boolean) {
        val key = vtKey()
        if (key.isEmpty()) {
            openVirusTotal(url)
            return
        }
        toast("A analisar no VirusTotal…")
        Thread {
            try {
                val r = VirusTotal.scan(key, url)
                runOnUiThread { showVtResult(url, r) }
            } catch (e: VtException) {
                Log.w(TAG, "VirusTotal: ${e.kind}", e)
                toast(
                    when (e.kind) {
                        VtError.BAD_KEY -> "Chave de API do VirusTotal inválida"
                        VtError.RATE_LIMIT -> "Limite do VirusTotal atingido, tenta mais tarde"
                        VtError.FAILED -> "Não foi possível analisar o site"
                    }
                )
            }
        }.start()
    }

    private fun showVtResult(url: String, r: VtResult) {
        if (r.flagged == 0) {
            toast("Nenhuma ameaça detetada (${r.total} motores)")
            return
        }
        Sheet(this, "⚠ Site suspeito", hostOf(url))
            .info("🚨", "Sinalizado por", "${r.flagged}/${r.total} motores de segurança")
            .text("Recomendamos sair deste site. Não introduzas palavras-passe nem dados pessoais.")
            .actions(
                Triple("Sair do site", BtnStyle.PRIMARY) { appLoad(homeUrl()) },
                Triple("Ver relatório", BtnStyle.SECONDARY) { appLoad(r.reportUrl) },
                Triple("Continuar por minha conta", BtnStyle.SECONDARY) {}
            )
            .show()
    }

    private fun showVirusTotalSettings() {
        val sh = Sheet(this, "VirusTotal", "Análise automática de sites suspeitos")
        sh.text("Cria uma conta gratuita em virustotal.com e cola aqui a tua chave de API (Perfil > API key). A chave fica só neste telemóvel.")
        val key = sh.input("Chave de API do VirusTotal", vtKey())
        val auto = sh.check("Analisar automaticamente sites sem ligação segura", vtAuto())
        sh.note("Privacidade: o endereço analisado é enviado ao VirusTotal. Nada é enviado se não ativares a opção nem carregares em Analisar.")
        fun save() {
            prefs().edit()
                .putString(KEY_VT_KEY, key.text.toString().trim())
                .putBoolean(KEY_VT_AUTO, auto.isChecked)
                .apply()
        }
        sh.actions(
            Triple("Guardar", BtnStyle.PRIMARY) {
                save()
                toast("Definições guardadas")
            },
            Triple("Analisar esta página", BtnStyle.SECONDARY) {
                save()
                val u = currentUrl
                if (u != null && (u.startsWith("https://") || u.startsWith("http://"))) {
                    runVirusTotal(u, quiet = false)
                } else toast("Abre primeiro uma página")
            },
            Triple("Fechar", BtnStyle.SECONDARY) {}
        ).show()
    }

    private fun openVirusTotal(url: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("link", url))
        toast("Link copiado: cola-o no VirusTotal")
        // Abre o VirusTotal num novo separador; o site continua aberto no anterior.
        openNewTab("https://www.virustotal.com/gui/home/url")
    }

    private fun appBack() {
        appNav = true
        session.goBack()
    }

    private fun appForward() {
        appNav = true
        session.goForward()
    }

    private fun appReload() {
        appNav = true
        session.reload()
    }

    // ---------------------------------------------------------------- delegates

    private fun setupDelegates(s: GeckoSession, tab: Tab) {
        attachMediaDelegate(s)

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {

            override fun onLocationChange(
                sess: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                tab.url = url
                if (tab === active) {
                    refreshAddressBar()
                    if (url != null && url.startsWith("http://") && url != tab.lastInsecure) {
                        tab.lastInsecure = url
                        runOnUiThread { warnInsecure(url) }
                    }
                }
            }

            override fun onCanGoBack(sess: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
                if (tab === active) updateNavButtons()
            }

            override fun onCanGoForward(sess: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
                if (tab === active) updateNavButtons()
            }

            override fun onLoadRequest(
                sess: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri

                // 1) Esquemas que abrem outras apps ou ficheiros (intent://, market://, file://…): bloqueados.
                val ok = uri.startsWith("https://") || uri.startsWith("http://") ||
                    uri.startsWith("about:") || uri.startsWith("resource:") ||
                    uri.startsWith("moz-extension:") || uri.startsWith("blob:")
                if (!ok) {
                    tab.appNav = false
                    return deny("Ligação externa bloqueada")
                }

                // Link direto para uma extensão (.xpi) em addons.mozilla.org: propõe instalar.
                if (isAmoXpi(uri)) {
                    tab.appNav = false
                    urlBar.post { confirmInstallXpi(uri) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // 2) Navegação iniciada por nós (carregar, voltar, avançar): sempre permitida.
                if (tab.appNav) {
                    tab.appNav = false
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                // 3) Pop-ups / novas janelas: só com clique do utilizador, e abrem num novo separador.
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    if (request.hasUserGesture) {
                        urlBar.post { openNewTab(uri) }
                    } else {
                        toast("Pop-up bloqueado")
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // 4) Redirecionamento por script, sem clique, para outro site: bloqueado.
                //    (Redirecionamentos HTTP do servidor, ex. login, continuam permitidos.)
                if (!request.hasUserGesture && !request.isDirectNavigation && !request.isRedirect &&
                    !sameSite(tab.url, uri)
                ) {
                    return deny("Redirecionamento bloqueado")
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(sess: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                // Nunca abrimos janelas extra (já tratado em onLoadRequest).
                return GeckoResult.fromValue(null)
            }
        }

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(sess: GeckoSession, url: String) {
                tab.blocked = 0
                tab.media.clear()
                tab.loading = true
                tab.progress = 5
                if (tab === active) {
                    updateShield()
                    updateMediaButton()
                    setLoadingUi(true)
                    progress.visibility = View.VISIBLE
                    progress.progress = 5
                }
            }

            override fun onProgressChange(sess: GeckoSession, p: Int) {
                tab.progress = p
                if (tab === active) progress.progress = p
            }

            override fun onPageStop(sess: GeckoSession, success: Boolean) {
                tab.appNav = false
                tab.loading = false
                if (tab === active) {
                    setLoadingUi(false)
                    progress.visibility = View.INVISIBLE
                }
                if (success) recordHistory(tab.url, tab.title)
            }
        }

        s.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(sess: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (event.isBlocking) {
                    tab.blocked++
                    if (tab === active) updateShield()
                }
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onContextMenu(
                sess: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                runOnUiThread { showContextMenu(element) }
            }

            override fun onTitleChange(sess: GeckoSession, title: String?) {
                tab.title = title
            }

            override fun onExternalResponse(sess: GeckoSession, response: WebResponse) {
                if (isAmoXpi(response.uri)) {
                    urlBar.post { confirmInstallXpi(response.uri) }
                } else {
                    startDownload(response)
                }
            }

            override fun onFullScreen(sess: GeckoSession, fullScreen: Boolean) {
                if (tab === active) runOnUiThread { setFullscreen(fullScreen) }
            }

            override fun onCrash(sess: GeckoSession) {
                recoverSession(tab)
            }

            override fun onKill(sess: GeckoSession) {
                recoverSession(tab)
            }
        }

        s.permissionDelegate = permissionDelegate()

        s.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                sess: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                showPromptDialog(r, { prompt.dismiss() }) {
                    setTitle(prompt.title ?: hostOf(tab.url) ?: "")
                    setMessage(prompt.message ?: "")
                    setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.dismiss()) }
                }
                return r
            }

            override fun onButtonPrompt(
                sess: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                showPromptDialog(r, { prompt.dismiss() }) {
                    setTitle(prompt.title ?: hostOf(tab.url) ?: "")
                    setMessage(prompt.message ?: "")
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        r.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
                    }
                    setNegativeButton(android.R.string.cancel) { _, _ ->
                        r.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                    }
                }
                return r
            }

            override fun onTextPrompt(
                sess: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val input = EditText(this@MainActivity)
                input.setText(prompt.defaultValue ?: "")
                showPromptDialog(r, { prompt.dismiss() }) {
                    setTitle(prompt.title ?: hostOf(tab.url) ?: "")
                    setMessage(prompt.message ?: "")
                    setView(input)
                    setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.confirm(input.text.toString())) }
                    setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
                }
                return r
            }

            override fun onAuthPrompt(
                sess: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val box = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 16, 48, 0)
                }
                val user = EditText(this@MainActivity).apply {
                    hint = I18n.tr("Utilizador")
                    inputType = InputType.TYPE_CLASS_TEXT
                }
                val pass = EditText(this@MainActivity).apply {
                    hint = I18n.tr("Palavra-passe")
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                val onlyPass = (prompt.authOptions.flags and
                    GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
                if (!onlyPass) box.addView(user)
                box.addView(pass)
                showPromptDialog(r, { prompt.dismiss() }) {
                    setTitle(prompt.title ?: hostOf(tab.url) ?: "")
                    setMessage(prompt.message ?: "")
                    setView(box)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        r.complete(
                            if (onlyPass) prompt.confirm(pass.text.toString())
                            else prompt.confirm(user.text.toString(), pass.text.toString())
                        )
                    }
                    setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
                }
                return r
            }

            // Listas de opções (<select>, menus).
            override fun onChoicePrompt(
                sess: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ChoicePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val flat = ArrayList<GeckoSession.PromptDelegate.ChoicePrompt.Choice>()
                fun walk(cs: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>?) {
                    cs?.forEach { c ->
                        val sub = c.items
                        if (sub != null && sub.isNotEmpty()) walk(sub)
                        else if (!c.separator) flat.add(c)
                    }
                }
                walk(prompt.choices)
                val labels = flat.map { it.label ?: "" }.toTypedArray()
                val multiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE
                val checked = BooleanArray(flat.size) { flat[it].selected }
                showPromptDialog(r, { prompt.dismiss() }) {
                    setTitle(prompt.title ?: hostOf(tab.url) ?: "")
                    if (multiple) {
                        setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            val ids = flat.indices.filter { checked[it] }.map { flat[it].id }.toTypedArray()
                            r.complete(prompt.confirm(ids))
                        }
                        setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
                    } else {
                        setItems(labels) { _, i -> r.complete(prompt.confirm(flat[i].id)) }
                    }
                }
                return r
            }

            override fun onFilePrompt(
                sess: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                pendingFile = Pair(prompt, result)

                val mimes = prompt.mimeTypes?.filter { it.contains('/') }.orEmpty()
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (mimes.size == 1) mimes[0] else "*/*"
                    if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                    putExtra(
                        Intent.EXTRA_ALLOW_MULTIPLE,
                        prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                    )
                }
                try {
                    startActivityForResult(Intent.createChooser(i, I18n.tr("Escolher ficheiro")), REQ_FILE)
                } catch (e: Exception) {
                    pendingFile = null
                    result.complete(prompt.dismiss())
                }
                return result
            }
        }
    }

    // ---------------------------------------------------------------- permissões

    private var pendingAndroidPerm: GeckoSession.PermissionDelegate.Callback? = null
    // Câmara/microfone já permitidos nesta utilização da app (site + tipo); esquece ao fechar a app.
    private val sessionAllowed = HashSet<String>()

    private fun askPermission(message: String, onAllow: () -> Unit, onDeny: () -> Unit) {
        runOnUiThread {
            try {
                if (isFinishing || isDestroyed) {
                    onDeny()
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle(I18n.tr("Permissão pedida"))
                    .setMessage(I18n.tr(message))
                    .setPositiveButton(I18n.tr("Permitir")) { _, _ -> onAllow() }
                    .setNegativeButton(I18n.tr("Bloquear")) { _, _ -> onDeny() }
                    .setOnCancelListener { onDeny() }
                    .show()
            } catch (e: Exception) {
                Log.w(TAG, "pedido de permissão falhou: $e")
                onDeny()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        val cb = pendingAndroidPerm ?: return
        pendingAndroidPerm = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) cb.grant() else cb.reject()
    }

    // ---------------------------------------------------------------- verificação de ficheiros

    private val watched = java.util.concurrent.ConcurrentHashMap<Long, String>()

    // Downloads feitos pelo DownloadManager: quando terminam, confere-se o início do ficheiro.
    private val dlReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val name = watched.remove(id) ?: return
            Thread {
                try {
                    val dm = c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val done = dm.query(DownloadManager.Query().setFilterById(id))?.use { cur ->
                        cur.moveToFirst() &&
                            cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                    } == true
                    if (!done) return@Thread
                    val head = ByteArray(64)
                    var n = 0
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(dm.openDownloadedFile(id)).use { st ->
                        while (n < head.size) {
                            val r = st.read(head, n, head.size - n)
                            if (r < 0) break
                            n += r
                        }
                    }
                    val v = FileCheck.check(head.copyOf(n), name)
                    if (!v.ok) runOnUiThread { warnFile(name, v) { dm.remove(id) } }
                } catch (e: SecurityException) {
                    Log.w(TAG, "verificação sem permissão: $e")
                } catch (e: Exception) {
                    Log.w(TAG, "verificação do ficheiro falhou: $e")
                }
            }.start()
        }
    }

    private fun warnFile(name: String, v: FileCheck.Verdict, remove: () -> Unit) {
        val sh = Sheet(this, if (v.dangerous) "🚨 Ficheiro perigoso" else "⚠ Ficheiro suspeito", name)
        sh.info("📄", "Extensão", if (v.ext.isEmpty()) "(nenhuma)" else ".${v.ext}")
        sh.info("🔍", "Conteúdo real", v.label ?: "?")
        sh.text(
            if (v.dangerous) "Pode ser um programa disfarçado de outro ficheiro. Recomendamos apagar."
            else "O ficheiro não corresponde à extensão. Pode estar corrompido, ser uma página de erro ou estar disfarçado."
        )
        sh.actions(
            Triple("Apagar", BtnStyle.DANGER) {
                try { remove() } catch (e: Exception) { Log.w(TAG, "apagar falhou: $e") }
                toast("Ficheiro apagado")
            },
            Triple("Manter", BtnStyle.SECONDARY) {}
        ).show()
    }

    /** Mostra um diálogo de página; se o utilizador o fechar (voltar/fora), responde com [dismissed]. */
    private fun showPromptDialog(
        result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
        dismissed: () -> GeckoSession.PromptDelegate.PromptResponse,
        build: AlertDialog.Builder.() -> Unit
    ) {
        runOnUiThread {
            try {
                if (isFinishing || isDestroyed) {
                    result.complete(dismissed())
                    return@runOnUiThread
                }
                val b = AlertDialog.Builder(this)
                b.build()
                b.setOnCancelListener { result.complete(dismissed()) }
                b.show()
            } catch (e: Exception) {
                Log.w(TAG, "diálogo da página falhou: $e")
                try { result.complete(dismissed()) } catch (_: Exception) { }
            }
        }
    }

    private fun permissionDelegate() = object : GeckoSession.PermissionDelegate {

        override fun onAndroidPermissionsRequest(
            sess: GeckoSession,
            permissions: Array<out String>?,
            callback: GeckoSession.PermissionDelegate.Callback
        ) {
            val needed = permissions.orEmpty().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) {
                callback.grant()
                return
            }
            pendingAndroidPerm?.reject()
            pendingAndroidPerm = callback
            try {
                requestPermissions(needed.toTypedArray(), REQ_PERM)
            } catch (e: SecurityException) {
                pendingAndroidPerm = null
                callback.reject()
            }
        }

        override fun onContentPermissionRequest(
            sess: GeckoSession,
            perm: GeckoSession.PermissionDelegate.ContentPermission
        ): GeckoResult<Int>? {
            val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            val host = hostOf(perm.uri) ?: perm.uri
            return when (perm.permission) {
                GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> {
                    val r = GeckoResult<Int>()
                    askPermission("$host quer saber a tua localização", { r.complete(allow) }, { r.complete(deny) })
                    r
                }
                // Inofensivas e necessárias a muitos sites (armazenamento, vídeo com DRM).
                GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> GeckoResult.fromValue(allow)
                // Notificações, realidade virtual, autoplay com som, etc.: bloqueadas.
                else -> GeckoResult.fromValue(deny)
            }
        }

        override fun onMediaPermissionRequest(
            sess: GeckoSession,
            uri: String,
            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback
        ) {
            val v = video?.firstOrNull()
            val a = audio?.firstOrNull()
            if (v == null && a == null) {
                callback.reject()
                return
            }
            val host = hostOf(uri) ?: uri
            val msg = when {
                v != null && a != null -> "$host quer usar a câmara e o microfone"
                v != null -> "$host quer usar a câmara"
                else -> "$host quer usar o microfone"
            }
            val key = "$host|${v != null}|${a != null}"
            if (key in sessionAllowed) {
                callback.grant(v, a)
                return
            }
            askPermission(msg, { sessionAllowed.add(key); callback.grant(v, a) }, { callback.reject() })
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FOLDER) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    prefs().edit().putString(KEY_DL_TREE, uri.toString()).apply()
                    toast("Pasta de downloads: ${folderLabel(uri)}")
                } catch (e: Exception) {
                    Log.w(TAG, "permissão da pasta falhou: $e")
                    toast("Não foi possível usar essa pasta")
                }
            }
            return
        }
        if (requestCode != REQ_FILE) return
        val (prompt, result) = pendingFile ?: return
        pendingFile = null

        if (resultCode != RESULT_OK || data == null) {
            result.complete(prompt.dismiss())
            return
        }
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            for (n in 0 until clip.itemCount) uris.add(clip.getItemAt(n).uri)
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) {
            result.complete(prompt.dismiss())
        } else if (uris.size == 1) {
            result.complete(prompt.confirm(this, uris[0]))
        } else {
            result.complete(prompt.confirm(this, uris.toTypedArray()))
        }
    }

    // ---------------------------------------------------------------- downloads

    // ---------------------------------------------------------------- histórico

    private fun historyOn() = prefs().getBoolean(KEY_HISTORY_ON, true)

    /** Lista de (título, endereço); a mais recente primeiro. */
    private fun loadHistory(): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val arr = org.json.JSONArray(prefs().getString(KEY_HISTORY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(o.getString("t") to o.getString("u"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "histórico ilegível: $e")
        }
        return out
    }

    private fun saveHistory(list: List<Pair<String, String>>) {
        val arr = org.json.JSONArray()
        list.take(HISTORY_MAX).forEach { arr.put(org.json.JSONObject().put("t", it.first).put("u", it.second)) }
        prefs().edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    // Só guarda com a opção ligada e fora do modo privado.
    private fun recordHistory(url: String?, title: String?) {
        if (!historyOn() || privateMode || url == null) return
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return
        val list = loadHistory()
        list.removeAll { it.second == url }
        val name = title?.trim().takeUnless { it.isNullOrEmpty() } ?: (hostOf(url) ?: url)
        list.add(0, name to url)
        saveHistory(list)
    }

    private fun showHistory() {
        val list = loadHistory()
        val on = historyOn()
        val sh = Sheet(this, "Histórico", if (list.isEmpty()) null else "${list.size} páginas")
        sh.card(
            icon = "🕘",
            title = "Guardar histórico",
            detail = "Não guarda no modo privado",
            toggle = Pair(on, { v: Boolean ->
                prefs().edit().putBoolean(KEY_HISTORY_ON, v).apply()
                toast(if (v) "Histórico ligado" else "Histórico desligado")
                Unit
            })
        )
        if (list.isEmpty()) {
            sh.text("Ainda não há páginas no histórico.")
        } else {
            list.take(60).forEach { (title, url) ->
                sh.card(
                    icon = title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                    title = title,
                    detail = url,
                    onClick = { appLoad(url) },
                    actions = listOf(Pair("✕", {
                        saveHistory(loadHistory().filter { it.second != url })
                        showHistory()
                    }))
                )
            }
            if (list.size > 60) sh.note("A mostrar as 60 mais recentes.")
        }
        if (list.isEmpty()) {
            sh.actions(Triple("Fechar", BtnStyle.PRIMARY) {})
        } else {
            sh.actions(
                Triple("Limpar histórico", BtnStyle.DANGER) {
                    prefs().edit().remove(KEY_HISTORY).apply()
                    toast("Histórico apagado")
                },
                Triple("Fechar", BtnStyle.SECONDARY) {}
            )
        }
        sh.show()
    }

    // ---------------------------------------------------------------- pressão longa

    private fun showContextMenu(el: GeckoSession.ContentDelegate.ContextElement) {
        val src = el.srcUri
        val link = el.linkUri
        val kind = when (el.type) {
            GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> "image"
            GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO -> "video"
            GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> "audio"
            else -> null
        }
        if (kind == null && link == null) return

        val title = when (kind) {
            "image" -> "Imagem"
            "video" -> "Vídeo"
            "audio" -> "Áudio"
            else -> "Link"
        }
        val sh = Sheet(this, title, hostOf(src ?: link))
        if (kind != null && src != null) {
            val saveLabel = when (kind) {
                "image" -> "Guardar imagem"
                "video" -> "Guardar vídeo"
                else -> "Guardar áudio"
            }
            sh.card("💾", saveLabel, "Transferências", onClick = {
                if (src.startsWith("http://") || src.startsWith("https://")) {
                    downloadUrl(src, null, null, true)
                } else {
                    toast("Este conteúdo não pode ser guardado (transmissão protegida ou em blocos)")
                }
            })
            sh.card("🔗", "Copiar link do ficheiro", null, onClick = { copyText(src) })
            sh.card("👁", "Abrir ficheiro", null, onClick = { appLoad(src) })
        }
        if (link != null) {
            sh.card("↗", "Abrir link", hostOf(link), onClick = { appLoad(link) })
            sh.card("📋", "Copiar link", null, onClick = { copyText(cleanUrl(link)) })
            sh.card("📤", "Partilhar link", null, onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, cleanUrl(link))
                }
                startActivity(Intent.createChooser(send, I18n.tr("Partilhar link")))
            })
        }
        sh.actions(Triple("Fechar", BtnStyle.SECONDARY) {}).show()
    }

    private fun copyText(text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("link", text))
        toast("Link copiado")
    }

    private fun startDownload(response: WebResponse) {
        val headers = response.headers
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val disp = header("Content-Disposition")
        val type = header("Content-Type")?.substringBefore(';')?.trim()
        val body = response.body
        if (body != null) {
            // O motor já descarregou com os cookies e o DNS da app: guardamos esse fluxo.
            saveStream(body, response.uri, disp, type)
        } else {
            downloadUrl(response.uri, disp, type, false)
        }
    }

    /** Nome de ficheiro seguro: sem separadores, caracteres de controlo nem pontos iniciais; tamanho limitado. */
    private fun safeName(url: String, disposition: String?, mime: String?): String {
        var n = URLUtil.guessFileName(url, disposition, mime)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimStart('.')
        if (n.isEmpty()) n = "download"
        if (n.length > 120) {
            val dot = n.lastIndexOf('.')
            n = if (dot > 0 && n.length - dot <= 10) n.take(110) + n.substring(dot) else n.take(120)
        }
        return n
    }

    /** Guarda um fluxo já aberto (em segundo plano). */
    private fun saveStream(body: java.io.InputStream, url: String, disposition: String?, mime: String?) {
        toast("A guardar: ${safeName(url, disposition, mime)}")
        Thread {
            try {
                saveBlocking(body, url, disposition, mime)
            } catch (e: Exception) {
                Log.w(TAG, "guardar ficheiro falhou: $e")
                toast("Falhou a transferência")
            } finally {
                try { body.close() } catch (e: Exception) { }
            }
        }.start()
    }

    /**
     * Guarda um fluxo na pasta escolhida (SAF) ou em Transferências (MediaStore, sem permissões), confere o tipo
     * real do ficheiro e avisa se não corresponder à extensão. Bloqueia: chamar fora da thread principal.
     * Se falhar, apaga o ficheiro parcial e lança a exceção.
     */
    private fun saveBlocking(body: java.io.InputStream, url: String, disposition: String?, mime: String?) {
        val name = safeName(url, disposition, mime)
        val type = mime.takeUnless { it.isNullOrEmpty() } ?: "application/octet-stream"
        val tree = prefs().getString(KEY_DL_TREE, null)
        var created: Uri? = null
        var viaStore = false
        val head = ByteArray(64)
        var n = 0

        fun removeSaved(u: Uri, store: Boolean) {
            try {
                if (store) contentResolver.delete(u, null, null)
                else android.provider.DocumentsContract.deleteDocument(contentResolver, u)
            } catch (e: Exception) {
                Log.w(TAG, "apagar falhou: $e")
            }
        }

        try {
            val out: java.io.OutputStream
            if (tree != null) {
                val t = Uri.parse(tree)
                val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    t, android.provider.DocumentsContract.getTreeDocumentId(t)
                )
                val doc = android.provider.DocumentsContract.createDocument(contentResolver, parent, type, name)
                    ?: throw java.io.IOException("não foi possível criar o ficheiro")
                created = doc
                out = contentResolver.openOutputStream(doc) ?: throw java.io.IOException("sem acesso à pasta")
            } else {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, type)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val u = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw java.io.IOException("não foi possível criar o ficheiro")
                created = u
                viaStore = true
                out = contentResolver.openOutputStream(u) ?: throw java.io.IOException("sem acesso a Transferências")
            }
            while (n < head.size) {
                val r = body.read(head, n, head.size - n)
                if (r < 0) break
                n += r
            }
            out.use { o ->
                o.write(head, 0, n)
                body.copyTo(o, 64 * 1024)
            }
            if (viaStore) {
                contentResolver.update(
                    created!!, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null
                )
            }
        } catch (e: Exception) {
            created?.let { removeSaved(it, viaStore) }
            throw e
        }

        val doc = created!!
        val store = viaStore
        val v = FileCheck.check(head.copyOf(n), name)
        if (v.ok) toast("Guardado: $name")
        else runOnUiThread { warnFile(name, v) { removeSaved(doc, store) } }
    }

    /** Descarrega um endereço http(s). Para imagens/vídeos/áudio (pressão longa) envia o site de origem como Referer. */
    private fun downloadUrl(url: String, disposition: String?, mime: String?, withReferer: Boolean) {
        if (!url.startsWith("https://")) {
            toast("Transferência não suportada (só ligações https)")
            return
        }
        // Pasta escolhida pelo utilizador: a app descarrega ela própria.
        val tree = prefs().getString(KEY_DL_TREE, null)
        if (tree != null) {
            saveToTree(url, disposition, mime, if (withReferer) currentUrl else null)
        } else {
            downloadViaManager(url, disposition, mime, withReferer)
        }
    }

    @SuppressLint("MissingPermission")
    private fun downloadViaManager(url: String, disposition: String?, mime: String?, withReferer: Boolean) {
        val name = safeName(url, disposition, mime)

        try {
            val req = DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (withReferer) currentUrl?.let { req.addRequestHeader("Referer", it) }
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            watched[id] = name
            toast("A transferir: $name")
        } catch (e: SecurityException) {
            Log.w(TAG, "download sem permissão: $e")
            toast("Falhou a transferência")
        } catch (e: Exception) {
            Log.w(TAG, "download falhou: $e")
            toast("Falhou a transferência")
        }
    }

    // ---------------------------------------------------------------- pasta de downloads

    /** Descarrega para a pasta escolhida (Storage Access Framework). Se falhar, recua para Transferências. */
    private fun saveToTree(url: String, disposition: String?, mime: String?, referer: String?) {
        toast("A guardar…")
        Thread {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) GabrielBrowser")
                if (referer != null) c.setRequestProperty("Referer", referer)
                try {
                    val code = c.responseCode
                    if (code !in 200..299) throw java.io.IOException("HTTP $code")
                    val type = c.contentType?.substringBefore(';')?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: mime ?: "application/octet-stream"
                    c.inputStream.use {
                        saveBlocking(it, url, c.getHeaderField("Content-Disposition") ?: disposition, type)
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "guardar na pasta escolhida falhou: $e")
                toast("Pasta indisponível: a guardar em Transferências")
                runOnUiThread { downloadViaManager(url, disposition, mime, referer != null) }
            }
        }.start()
    }

    private fun folderLabel(tree: Uri): String = try {
        val id = android.provider.DocumentsContract.getTreeDocumentId(tree)
        val vol = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        (if (vol == "primary") "Telemóvel" else vol) + if (path.isEmpty()) "" else " / " + path.replace("/", " / ")
    } catch (e: Exception) {
        tree.lastPathSegment ?: tree.toString()
    }

    private fun showDownloadFolder() {
        val saved = prefs().getString(KEY_DL_TREE, null)
        val sh = Sheet(this, "Pasta de downloads", "Onde os ficheiros são guardados")
        sh.card(
            icon = if (saved == null) "✓" else "📥",
            title = "Transferências",
            detail = I18n.tr("Pasta predefinida do Android") + if (saved == null) "  ·  " + I18n.tr("atual") else "",
            onClick = {
                prefs().edit().remove(KEY_DL_TREE).apply()
                toast("Pasta: Transferências")
            }
        )
        if (saved != null) {
            sh.card(icon = "✓", title = "Pasta escolhida", detail = folderLabel(Uri.parse(saved)) + "  ·  " + I18n.tr("atual"))
        }
        sh.card(
            icon = "📂",
            title = "Escolher outra pasta…",
            detail = "Qualquer pasta do telemóvel ou do cartão",
            onClick = {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                try {
                    startActivityForResult(i, REQ_FOLDER)
                } catch (e: Exception) {
                    toast("Não foi possível abrir o seletor de pastas")
                }
            }
        )
        sh.note("Numa pasta escolhida a app guarda sem notificação de progresso (aparece um aviso quando termina). Só ligações https.")
        sh.actions(Triple("Fechar", BtnStyle.SECONDARY) {}).show()
    }

    // ---------------------------------------------------------------- ecrã inteiro

    @Suppress("DEPRECATION")
    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val vis = if (on) View.GONE else View.VISIBLE
        topBar.visibility = vis
        bottomBar.visibility = vis
        divider.visibility = vis
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { c ->
                if (on) {
                    c.hide(WindowInsets.Type.systemBars())
                    c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    c.show(WindowInsets.Type.systemBars())
                }
            }
        } else {
            window.decorView.systemUiVisibility =
                if (on) {
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                } else {
                    View.SYSTEM_UI_FLAG_VISIBLE
                }
        }
    }

    // ---------------------------------------------------------------- interface

    private fun bindViews() {
        geckoView = findViewById(R.id.gecko)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        divider = findViewById(R.id.divider)
        urlBar = findViewById(R.id.urlBar)
        urlBar.hint = I18n.tr("Pesquisar ou escrever endereço")
        siteIcon = findViewById(R.id.siteIcon)
        btnClear = findViewById(R.id.btnClear)
        shieldBox = findViewById(R.id.shieldBox)
        shieldCount = findViewById(R.id.shieldCount)
        progress = findViewById(R.id.progress)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnHome = findViewById(R.id.btnHome)
        btnReload = findViewById(R.id.btnReload)
        btnMenu = findViewById(R.id.btnMenu)
        btnTabs = findViewById(R.id.btnTabs)
        mediaBtn = findViewById(R.id.mediaBtn)
        tabCount = findViewById(R.id.tabCount)
    }

    private fun setupUi() {
        urlBar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                appLoad(normalize(v.text.toString()))
                urlBar.clearFocus()
                hideKeyboard()
                geckoView.requestFocus()
                true
            } else {
                false
            }
        }

        urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                urlBar.setText(currentUrl ?: "")
                urlBar.post { urlBar.selectAll() }
                btnClear.visibility = View.VISIBLE
            } else {
                btnClear.visibility = View.GONE
                urlBar.setText(displayUrl(currentUrl))
            }
        }

        btnClear.setOnClickListener { urlBar.setText("") }
        btnBack.setOnClickListener { if (canGoBack) appBack() }
        btnForward.setOnClickListener { if (canGoForward) appForward() }
        btnHome.setOnClickListener { appLoad(homeUrl()) }
        btnReload.setOnClickListener { if (loading) session.stop() else appReload() }
        btnMenu.setOnClickListener { showMenu() }
        mediaBtn.setOnClickListener { showMedia() }
        btnTabs.setOnClickListener { showTabs() }
        btnTabs.setOnLongClickListener {
            openNewTab(null)
            true
        }
        shieldBox.setOnClickListener { showProtection() }
    }

    private fun updateNavButtons() {
        btnBack.isEnabled = canGoBack
        btnForward.isEnabled = canGoForward
    }

    private fun setLoadingUi(on: Boolean) {
        btnReload.setImageResource(if (on) R.drawable.ic_close else R.drawable.ic_refresh)
        btnReload.contentDescription = I18n.tr(if (on) "Parar" else "Recarregar")
    }

    private fun updateShield() {
        runOnUiThread {
            if (blockedCount > 0) {
                shieldCount.text = blockedCount.toString()
                shieldCount.visibility = View.VISIBLE
            } else {
                shieldCount.visibility = View.GONE
            }
        }
    }

    private fun refreshAddressBar() {
        runOnUiThread {
            if (!urlBar.hasFocus()) urlBar.setText(displayUrl(currentUrl))
            val secure = currentUrl?.startsWith("https://") == true
            siteIcon.setImageResource(if (secure) R.drawable.ic_lock else R.drawable.ic_search)
            siteIcon.imageTintList = ColorStateList.valueOf(
                getColor(if (secure) R.color.accent else R.color.icon)
            )
        }
    }

    private fun displayUrl(url: String?): String {
        if (url == null || url.startsWith("about:")) return ""
        return hostOf(url) ?: url
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    // ---------------------------------------------------------------- menu e diálogos

    private fun showMenu() {
        val url = currentUrl
        val faved = url?.let { u -> loadBookmarks().any { it.second == u } } == true
        Sheet(this, "Gabriel Browser", hostOf(url) ?: "Menu")
            .tiles(
                listOf(
                    Tile(if (faved) "★" else "☆", if (faved) "Nos favoritos" else "Favorito", faved) { toggleBookmark() },
                    Tile("➕", "Novo separador") { openNewTab(null) },
                    Tile("🗂", "Separadores") { showTabs() },
                    Tile("📚", "Favoritos") { showBookmarks() },
                    Tile("🕘", "Histórico") { showHistory() },
                    Tile("🔗", "Partilhar") { shareLink() },
                    Tile("📋", "Copiar link") { copyLink() },
                    Tile("🛡", "Proteção") { showProtection() },
                    Tile("📁", "Pasta") { showDownloadFolder() },
                    Tile("🧹", "Limpar dados") { showClearData() },
                    Tile("🕶", "Privado", privateMode) { togglePrivateMode() },
                    Tile("🖥", "Site PC", desktopMode) {
                        desktopMode = !desktopMode
                        applyDesktopMode(session)
                        appReload()
                    },
                    Tile("🧩", "Extensões") { showExtensions() },
                    Tile("🏠", "Página inicial") { showHomeSettings() },
                    Tile("🔎", "Pesquisa") { showEngineChooser() },
                    Tile("🦠", "VirusTotal") { showVirusTotalSettings() },
                    Tile("🌐", "DNS") { showDnsSettings() },
                    Tile("🌍", "Idioma") { showLanguage() },
                    Tile("ℹ", "Sobre") { showAbout() }
                ),
                cols = 4
            )
            .actions(Triple("Fechar", BtnStyle.SECONDARY) {})
            .show()
    }

    private fun shareLink() {
        val url = currentUrl
        if (url == null || url.startsWith("about:")) {
            toast("Nada para partilhar")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cleanUrl(url))
        }
        startActivity(Intent.createChooser(send, I18n.tr("Partilhar link")))
    }

    private fun copyLink() {
        val url = currentUrl
        if (url == null || url.startsWith("about:")) {
            toast("Nada para copiar")
            return
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("link", cleanUrl(url)))
        toast("Link copiado sem parâmetros de rastreio")
    }

    private fun showClearData() {
        val sh = Sheet(this, "Limpar dados", "Escolhe o que queres apagar")
        val cCookies = sh.check("🍪  Cookies", true)
        val cCache = sh.check("🗂  Cache (ficheiros temporários e imagens)", true)
        val cDom = sh.check("💾  Dados de sites (armazenamento local)", true)
        val cPerm = sh.check("🔐  Permissões e definições de sites", false)
        val cFav = sh.check("★  Favoritos", false)
        val cHist = sh.check("🕘  Histórico", true)
        sh.actions(
            Triple("Cancelar", BtnStyle.SECONDARY) {},
            Triple("Limpar", BtnStyle.DANGER) {
                var flags = 0L
                if (cCookies.isChecked) flags = flags or StorageController.ClearFlags.COOKIES
                if (cCache.isChecked) flags = flags or StorageController.ClearFlags.NETWORK_CACHE or
                    StorageController.ClearFlags.IMAGE_CACHE
                if (cDom.isChecked) flags = flags or StorageController.ClearFlags.DOM_STORAGES
                if (cPerm.isChecked) flags = flags or StorageController.ClearFlags.SITE_SETTINGS or
                    StorageController.ClearFlags.PERMISSIONS
                if (cFav.isChecked) prefs().edit().remove(KEY_BOOKMARKS).apply()
                if (cHist.isChecked) prefs().edit().remove(KEY_HISTORY).apply()
                if (flags == 0L) {
                    toast(if (cFav.isChecked || cHist.isChecked) "Dados apagados" else "Nada selecionado")
                } else {
                    rt.storageController.clearData(flags).accept(
                        {
                            toast("Dados apagados")
                            appLoad(homeUrl())
                        },
                        { toast("Falhou a limpeza") }
                    )
                }
            }
        ).show()
    }

    private fun wipeSite() {
        val host = hostOf(currentUrl)
        if (host == null) {
            toast("Sem site para limpar")
            return
        }
        rt.storageController.clearDataFromHost(host, StorageController.ClearFlags.ALL)
            .accept(
                {
                    toast("Dados de $host apagados")
                    appLoad(homeUrl())
                },
                { toast("Falhou a limpeza de $host") }
            )
    }

    private fun showProtection() {
        val host = hostOf(currentUrl)
        val secure = currentUrl?.startsWith("https://") == true
        val doh = prefs().getString(KEY_DOH, DEFAULT_DOH)?.trim().orEmpty()
        Sheet(this, host ?: "Proteção", "O que está a proteger-te nesta página")
            .info("🔒", "Ligação", if (secure) "Segura (HTTPS)" else "Sem página aberta")
            .info("🛡", "Rastreadores bloqueados", "$blockedCount nesta página")
            .info("🚫", "Anúncios (uBlock Origin)", if (ublockOn) "Ativo" else "Não carregado")
            .info("↩", "Pop-ups e redirecionamentos", "Bloqueados sem clique")
            .info("🌐", "DNS", if (doh.isEmpty()) "Do sistema" else "DoH (${hostOf(doh) ?: doh})")
            .actions(
                Triple("Limpar dados do site", BtnStyle.SECONDARY) { wipeSite() },
                Triple("Fechar", BtnStyle.PRIMARY) {}
            )
            .show()
    }

    private fun showEngineChooser() {
        val current = prefs().getInt(KEY_ENGINE, 0).coerceIn(ENGINES.indices)
        val sh = Sheet(this, "Motor de pesquisa", "Usado na barra de endereço")
        ENGINES.forEachIndexed { i, e ->
            sh.card(
                icon = if (i == current) "✓" else "🔎",
                title = e.name,
                detail = I18n.tr(e.desc) + "\n" + (hostOf(e.home) ?: e.home) +
                    if (i == current) "  ·  " + I18n.tr("atual") else "",
                onClick = {
                    prefs().edit().putInt(KEY_ENGINE, i).apply()
                    toast("Motor de pesquisa: ${e.name}")
                }
            )
        }
        sh.actions(Triple("Fechar", BtnStyle.SECONDARY) {}).show()
    }

    private fun showDnsSettings() {
        val sh = Sheet(this, "Servidor DNS (DoH)", "Usado só nesta app. Aplica-se após reiniciar.")
        val input = sh.input("https://freedns.controld.com/p2  (vazio = DNS do sistema)",
            prefs().getString(KEY_DOH, DEFAULT_DOH).orEmpty())
        sh.note("Predefinido: Control D p2 (bloqueia malware e anúncios).")
        sh.actions(
            Triple("Guardar e reiniciar", BtnStyle.PRIMARY) {
                prefs().edit().putString(KEY_DOH, input.text.toString().trim()).apply()
                restartApp()
            },
            Triple("Repor p2", BtnStyle.SECONDARY) {
                prefs().edit().putString(KEY_DOH, DEFAULT_DOH).apply()
                restartApp()
            },
            Triple("Cancelar", BtnStyle.SECONDARY) {}
        ).show()
    }

    // ---------------------------------------------------------------- favoritos

    private fun loadBookmarks(): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val arr = org.json.JSONArray(prefs().getString(KEY_BOOKMARKS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(o.getString("t") to o.getString("u"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "favoritos ilegíveis: $e")
        }
        return out
    }

    private fun saveBookmarks(list: List<Pair<String, String>>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("t", it.first).put("u", it.second)) }
        prefs().edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }

    private fun toggleBookmark() {
        val url = currentUrl
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            toast("Abre primeiro uma página")
            return
        }
        val list = loadBookmarks()
        if (list.removeAll { it.second == url }) {
            saveBookmarks(list)
            toast("Removido dos favoritos")
        } else {
            list.add(0, (hostOf(url) ?: url) to url)
            saveBookmarks(list)
            toast("Adicionado aos favoritos")
        }
    }

    private fun showBookmarks() {
        val list = loadBookmarks()
        val sh = Sheet(this, "Favoritos", if (list.isEmpty()) null else "${list.size} guardado(s)")
        if (list.isEmpty()) {
            sh.text("Ainda não tens favoritos.")
            sh.note("Abre uma página e usa Menu > Favorito.")
        } else {
            list.forEach { (title, url) ->
                sh.card(
                    icon = title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "★",
                    title = title,
                    detail = url,
                    onClick = { appLoad(url) },
                    actions = listOf(Pair("✕", {
                        saveBookmarks(loadBookmarks().filter { it.second != url })
                        toast("Removido")
                        showBookmarks()
                    }))
                )
            }
        }
        sh.actions(Triple("Fechar", BtnStyle.SECONDARY) {}).show()
    }



    // ---------------------------------------------------------------- extensões

    private fun showExtensions() {
        rt.webExtensionController.list().accept(
            { list ->
                runOnUiThread { showExtensionsDialog(list ?: emptyList()) }
            },
            { toast("Não foi possível listar as extensões") }
        )
    }

    /** Versão do uBlock Origin embutido, lida do manifest.json dos assets ("" se não existir). */
    private fun ublockVersion(): String =
        try {
            val text = assets.open("ublock/manifest.json").bufferedReader().use { it.readText() }
            Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.trim().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "não consegui ler assets/ublock/manifest.json: $e")
            ""
        }

    /** Versão da extensão; nas embutidas o motor pode não a indicar, por isso lê-se do manifest.json. */
    private fun extVersion(ext: WebExtension): String {
        var v = ""
        try { v = ext.metaData?.version ?: "" } catch (e: Exception) { }
        if (v.isNotBlank()) return v.trim()
        val dir = when {
            ext.id == MEDIA_ID -> "media"
            ext.isBuiltIn || ext.id.contains("ublock", ignoreCase = true) -> "ublock"
            else -> return ""
        }
        try {
            val text = assets.open("$dir/manifest.json").bufferedReader().use { it.readText() }
            val m = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)
            return m?.groupValues?.get(1)?.trim().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "não consegui ler assets/$dir/manifest.json: $e")
        }
        return ""
    }

    private fun showExtensionsDialog(list: List<WebExtension>) {
        val ctl = rt.webExtensionController
        val sh = Sheet(this, "Extensões", if (list.isEmpty()) null else "${list.size} instalada(s)")
        if (list.isEmpty()) sh.text("Nenhuma extensão instalada.")
        list.forEach { ext ->
            val name = ext.metaData.name ?: ext.id
            val kind = if (ext.isBuiltIn) "Embutida" else "Instalada"
            sh.card(
                icon = "🧩",
                title = name,
                detail = extVersion(ext).let { v -> if (v.isEmpty()) kind else "$kind · v$v" },
                toggle = Pair(ext.metaData.enabled, { on: Boolean ->
                    val r = if (on) ctl.enable(ext, WebExtensionController.EnableSource.USER)
                    else ctl.disable(ext, WebExtensionController.EnableSource.USER)
                    r.accept({ toast(if (on) "Ativada" else "Desativada") }, { toast("Falhou: ${it?.message}") })
                    Unit
                }),
                actions = if (ext.isBuiltIn) emptyList() else listOf(Pair("🗑", {
                    ctl.uninstall(ext).accept(
                        { toast("Extensão removida"); showExtensions() },
                        { toast("Falhou: ${it?.message}") }
                    )
                    Unit
                }))
            )
        }
        sh.actions(
            Triple("Adicionar extensão", BtnStyle.PRIMARY) { showAddExtension() },
            Triple("Fechar", BtnStyle.SECONDARY) {}
        ).show()
    }

    private fun showAddExtension() {
        Sheet(this, "Adicionar extensão", "Escolhe a origem")
            .card("🏪", "Loja de extensões", "addons.mozilla.org · toca no link de descarga (.xpi)", onClick = {
                toast("Abre a extensão e toca no link de descarga (.xpi) para a instalar")
                appLoad("https://addons.mozilla.org/firefox/extensions/")
            })
            .card("🛡", "Malwarebytes Browser Guard", "Proteção contra malware e phishing", onClick = { confirmBrowserGuard() })
            .card("🔗", "Instalar por link", "Cola um link .xpi", onClick = { askExtensionUrl() })
            .actions(Triple("Cancelar", BtnStyle.SECONDARY) {})
            .show()
    }

    private fun isAmoXpi(url: String?): Boolean {
        if (url == null) return false
        val u = try { Uri.parse(url) } catch (e: Exception) { return false }
        val path = u.path ?: return false
        return u.scheme == "https" &&
            (u.host == "addons.mozilla.org" || u.host == "addons.cdn.mozilla.net") &&
            (path.endsWith(".xpi") || path.contains("/downloads/file/") || path.contains("/downloads/latest/"))
    }

    private fun confirmInstallXpi(url: String) {
        Sheet(this, "Instalar extensão?", "De addons.mozilla.org")
            .note(url)
            .actions(
                Triple("Cancelar", BtnStyle.SECONDARY) {},
                Triple("Continuar", BtnStyle.PRIMARY) { installExtension(url) }
            )
            .show()
    }



    private fun confirmBrowserGuard() {
        Sheet(this, "Malwarebytes Browser Guard", "Descarregado de addons.mozilla.org")
            .text(
                "Já tens o uBlock Origin: se ambos bloquearem anúncios pode haver sobreposição. " +
                    "Desativa o bloqueio de anúncios no Browser Guard e mantém a proteção contra malware e phishing."
            )
            .note("A compatibilidade com o Android não está confirmada.")
            .actions(
                Triple("Cancelar", BtnStyle.SECONDARY) {},
                Triple("Instalar", BtnStyle.PRIMARY) { installExtension(BROWSER_GUARD_URL) }
            )
            .show()
    }

    private fun askExtensionUrl() {
        val sh = Sheet(this, "Instalar por link", "Link direto de um ficheiro .xpi")
        val input = sh.input("https://addons.mozilla.org/…/ficheiro.xpi")
        sh.note("Instala só extensões em que confias; de preferência de addons.mozilla.org.")
        sh.actions(
            Triple("Cancelar", BtnStyle.SECONDARY) {},
            Triple("Instalar", BtnStyle.PRIMARY) {
                val u = input.text.toString().trim()
                if (u.startsWith("https://") && u.contains(".xpi")) installExtension(u)
                else toast("Link inválido: tem de ser https e terminar em .xpi")
            }
        ).show()
    }

    private fun installExtension(url: String) {
        toast("A descarregar extensão…")
        rt.webExtensionController.install(url).accept(
            { ext -> toast("Instalada: ${ext?.metaData?.name ?: "extensão"}"); appReload() },
            { Log.w(TAG, "Falha ao instalar extensão: $it"); toast("Não foi possível instalar: ${it?.message}") }
        )
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        Sheet(this, "Gabriel Browser", "Versão $version")
            .text(
                "Browser leve e privado sobre GeckoView (motor do Firefox). " +
                    "Em homenagem à nau São Gabriel, nau-capitânia de Vasco da Gama na viagem à Índia " +
                    "(1497–1499): um browser também é um explorador."
            )
            .info("🌐", "DNS sobre HTTPS", "Só nesta app")
            .info("🛡", "Proteção estrita", "Rastreio e só HTTPS")
            .info("🚫", "uBlock Origin", ublockVersion().let { v -> if (v.isEmpty()) "Embutido" else "Embutido · v$v" })
            .info("↩", "Pop-ups e redirecionamentos", "Bloqueados")
            .card("🦠", "VirusTotal", "Análise automática de sites suspeitos", onClick = { showVirusTotalSettings() })
            .note("Mantém o motor atualizado: a segurança depende disso.")
            .actions(Triple("Fechar", BtnStyle.PRIMARY) {})
            .show()
    }

    private fun restartApp() {
        toast("A reiniciar para aplicar o DNS…")
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    // ---------------------------------------------------------------- utilitários

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return homeUrl()
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("about:")) return s
        val looksLikeHost = !s.contains(' ') && s.contains('.')
        return if (looksLikeHost) "https://$s" else engine().query + Uri.encode(s)
    }

    private fun deny(msg: String): GeckoResult<AllowOrDeny> {
        toast(msg)
        return GeckoResult.fromValue(AllowOrDeny.DENY)
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, I18n.tr(msg), Toast.LENGTH_SHORT).show() }
    }

    private fun hostOf(url: String?): String? = try {
        Uri.parse(url).host?.removePrefix("www.")?.lowercase()
    } catch (e: Exception) {
        null
    }

    private fun sameSite(a: String?, b: String?): Boolean {
        val ha = hostOf(a) ?: return false
        val hb = hostOf(b) ?: return false
        return ha == hb || ha.endsWith(".$hb") || hb.endsWith(".$ha")
    }

    private fun cleanUrl(url: String): String = try {
        val u = Uri.parse(url)
        if (u.isOpaque) {
            url
        } else {
            val b = u.buildUpon().clearQuery()
            for (name in u.queryParameterNames) {
                val low = name.lowercase()
                if (low.startsWith("utm_") || low in TRACKING_PARAMS) continue
                for (v in u.getQueryParameters(name)) b.appendQueryParameter(name, v)
            }
            b.build().toString()
        }
    } catch (e: Exception) {
        url
    }
}
