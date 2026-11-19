package pt.gabriel.browser

import android.net.Uri
import org.mozilla.geckoview.WebRequestError

/**
 * Páginas de erro de carregamento (ligação não privada, sem HTTPS, falha de rede).
 * Devolvidas por NavigationDelegate.onLoadError como URI data:. Os botões usam as funções que o
 * GeckoView expõe só a páginas de erro: document.addCertException() e document.reloadWithHttpsOnlyException().
 */
object ErrorPage {

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun build(failedUri: String?, error: WebRequestError, homeUrl: String): String {
        val host = try { Uri.parse(failedUri ?: "").host } catch (e: Exception) { null } ?: (failedUri ?: "")
        val t = { s: String -> esc(I18n.tr(s)) }
        val code = "0x" + Integer.toHexString(error.code).uppercase()

        val title: String
        val body: String
        val extra: String
        val buttons: String
        when {
            error.category == WebRequestError.ERROR_CATEGORY_SECURITY -> {
                val hsts = error.code == WebRequestError.ERROR_BAD_HSTS_CERT
                title = t("A tua ligação não é privada")
                body = esc(I18n.tr("Podem estar a tentar roubar as tuas informações em $host (por exemplo palavras-passe, mensagens ou cartões de crédito)."))
                extra = esc(I18n.tr("O certificado deste site não é de confiança ou é inválido. Código: $code"))
                buttons = if (hsts) {
                    "<p class=n>${t("Este site não permite continuar com um certificado inválido (HSTS).")}</p>"
                } else {
                    "<p class=n>${t("Continuar expõe os teus dados a quem controla a ligação. Não introduzas palavras-passe nem dados pessoais.")}</p>" +
                        "<button class=r onclick=\"go()\">${esc(I18n.tr("Continuar para $host (não seguro)"))}</button>"
                }
            }
            error.code == WebRequestError.ERROR_HTTPS_ONLY -> {
                title = t("Este site não suporta HTTPS")
                body = t("A app só liga por HTTPS. Podes continuar por HTTP, mas os dados não ficam protegidos.")
                extra = esc(I18n.tr("Código: $code"))
                buttons = "<button class=r onclick=\"http()\">${t("Continuar sem HTTPS")}</button>"
            }
            else -> {
                title = t("Não foi possível abrir a página")
                body = t("Verifica a ligação à internet e o endereço.")
                extra = esc(I18n.tr("Código: $code"))
                buttons = ""
            }
        }
        val security = error.category == WebRequestError.ERROR_CATEGORY_SECURITY || error.code == WebRequestError.ERROR_HTTPS_ONLY
        val primary = if (security) t("Voltar à segurança") else t("Recarregar")
        val primaryJs = if (security) "location.href='${esc(homeUrl).replace("'", "%27")}'" else "location.reload()"

        return """<!doctype html><html><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<style>
body{margin:0;padding:28px 22px;background:#202124;color:#bdc1c6;font:16px/1.6 sans-serif}
h1{font-weight:400;color:#e8eaed;font-size:26px;margin:22px 0 12px}
.i{width:56px;height:56px;border-radius:12px;background:#d93025;color:#fff;font-size:34px;font-weight:700;text-align:center;line-height:56px}
button{display:block;width:100%;border:0;border-radius:24px;padding:14px;font-size:16px;margin-top:14px}
.p{background:#8ab4f8;color:#202124;margin-top:28px}
.r{background:transparent;color:#f28b82;border:1px solid #f28b82}
a{color:#8ab4f8}#d{display:none;margin-top:18px}.n{font-size:14px}
</style></head><body>
<div class=i>!</div>
<h1>$title</h1><p>$body</p>
<button class=p onclick="$primaryJs">$primary</button>
<p><a href="#" onclick="document.getElementById('d').style.display='block';this.style.display='none';return false">${t("Avançadas")}</a></p>
<div id=d><p class=n>$extra</p>$buttons</div>
<script>
function go(){try{document.addCertException(true).then(function(){location.reload()},function(){location.reload()})}catch(e){location.reload()}}
function http(){try{document.reloadWithHttpsOnlyException()}catch(e){location.reload()}}
</script></body></html>"""
    }

    fun toUri(html: String): String = "data:text/html;charset=utf-8," + Uri.encode(html)
}
