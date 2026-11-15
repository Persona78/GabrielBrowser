package pt.gabriel.browser

import java.util.Locale

/**
 * Traduções da interface. O texto de origem é o português de Portugal: qualquer texto sem
 * tradução fica em português. Entradas com "{}" são modelos (a parte variável é preservada).
 *
 * Idiomas: pt-PT, pt-BR, en-US, en-GB, es, fr.
 */
object I18n {

    val LANGS = listOf(
        "pt-PT" to "Português (Portugal)",
        "pt-BR" to "Português (Brasil)",
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "es" to "Español",
        "fr" to "Français"
    )

    @Volatile
    var lang: String = "pt-PT"
        private set

    /** pref: "auto" (idioma do sistema) ou um dos códigos de LANGS. */
    fun setup(pref: String?) {
        lang = if (pref != null && pref != "auto" && LANGS.any { it.first == pref }) pref else fromSystem()
    }

    private fun fromSystem(): String {
        val l = Locale.getDefault()
        return when (l.language) {
            "pt" -> if (l.country.equals("BR", true)) "pt-BR" else "pt-PT"
            "en" -> if (l.country.equals("US", true)) "en-US" else "en-GB"
            "es" -> "es"
            "fr" -> "fr"
            else -> "en-GB"
        }
    }

    private class Table {
        val exact = HashMap<String, String>()
        val templates = ArrayList<Pair<Regex, String>>()
        fun put(pt: String, tr: String) {
            if (pt.contains("{}")) {
                val re = pt.split("{}").joinToString("(.+)") { Regex.escape(it) }
                templates.add(Regex(re, RegexOption.DOT_MATCHES_ALL) to tr)
            } else {
                exact[pt] = tr
            }
        }
    }

    private val tables = HashMap<String, Table>()
    private fun table(l: String) = tables.getOrPut(l) { Table() }

    private fun r(pt: String, en: String, es: String, fr: String, br: String? = null, us: String? = null) {
        table("en-GB").put(pt, en)
        table("en-US").put(pt, us ?: en)
        table("es").put(pt, es)
        table("fr").put(pt, fr)
        if (br != null) table("pt-BR").put(pt, br)
    }

    fun tr(s: String): String {
        if (s.isEmpty() || lang == "pt-PT") return s
        val t = tables[lang] ?: return s
        t.exact[s]?.let { return it }
        for ((re, out) in t.templates) {
            val m = re.matchEntire(s) ?: continue
            var res = out
            for (g in m.groupValues.drop(1)) res = res.replaceFirst("{}", g)
            return res
        }
        return s
    }

    init {
        // --- menu e geral
        r("Menu", "Menu", "Menú", "Menu")
        r("Nos favoritos", "Bookmarked", "En favoritos", "Dans les favoris")
        r("Favorito", "Bookmark", "Favorito", "Favori")
        r("Favoritos", "Bookmarks", "Favoritos", "Favoris")
        r("Partilhar", "Share", "Compartir", "Partager", "Compartilhar")
        r("Copiar link", "Copy link", "Copiar enlace", "Copier le lien")
        r("Proteção", "Protection", "Protección", "Protection")
        r("Limpar dados", "Clear data", "Borrar datos", "Effacer les données")
        r("Privado", "Private", "Privado", "Privé")
        r("Site PC", "Desktop site", "Sitio de escritorio", "Version bureau")
        r("Extensões", "Extensions", "Extensiones", "Extensions")
        r("Página inicial", "Home page", "Página de inicio", "Page d'accueil", us = "Homepage")
        r("Pesquisa", "Search", "Búsqueda", "Recherche")
        r("Sobre", "About", "Acerca de", "À propos")
        r("Idioma", "Language", "Idioma", "Langue")
        r("Fechar", "Close", "Cerrar", "Fermer")
        r("Cancelar", "Cancel", "Cancelar", "Annuler")
        r("Guardar", "Save", "Guardar", "Enregistrer", "Salvar")
        r("Adicionar", "Add", "Añadir", "Ajouter")
        r("Instalar", "Install", "Instalar", "Installer")
        r("Continuar", "Continue", "Continuar", "Continuer")
        r("Limpar", "Clear", "Borrar", "Effacer")
        r("Removido", "Removed", "Eliminado", "Retiré")

        // --- motores de pesquisa e botões
        r("DuckDuckGo (limpo)", "DuckDuckGo (clean)", "DuckDuckGo (limpio)", "DuckDuckGo (épuré)")
        r("DuckDuckGo (completo)", "DuckDuckGo (full)", "DuckDuckGo (completo)", "DuckDuckGo (complet)")
        r("Parar", "Stop", "Detener", "Arrêter")
        r("Recarregar", "Reload", "Recargar", "Actualiser", "Recarregar")

        // --- ligação não segura / VirusTotal
        r("Este site não tem ligação segura", "This site does not have a secure connection", "Este sitio no tiene una conexión segura", "Ce site n'a pas de connexion sécurisée", "Este site não tem conexão segura")
        r("Ligação não segura", "Insecure connection", "Conexión no segura", "Connexion non sécurisée", "Conexão não segura")
        r("Os dados enviados para este site podem ser lidos por terceiros. Não introduzas palavras-passe nem dados pessoais.", "Data sent to this site can be read by third parties. Do not enter passwords or personal data.", "Los datos enviados a este sitio pueden ser leídos por terceros. No introduzcas contraseñas ni datos personales.", "Les données envoyées à ce site peuvent être lues par des tiers. Ne saisissez ni mots de passe ni données personnelles.", "Os dados enviados para este site podem ser lidos por terceiros. Não digite senhas nem dados pessoais.")
        r("O VirusTotal analisa o endereço com dezenas de motores de segurança.", "VirusTotal checks the address with dozens of security engines.", "VirusTotal analiza la dirección con decenas de motores de seguridad.", "VirusTotal analyse l'adresse avec des dizaines de moteurs de sécurité.")
        r("Analisar no VirusTotal", "Scan with VirusTotal", "Analizar en VirusTotal", "Analyser avec VirusTotal")
        r("Link copiado: cola-o no VirusTotal", "Link copied: paste it into VirusTotal", "Enlace copiado: pégalo en VirusTotal", "Lien copié : collez-le dans VirusTotal", "Link copiado: cole no VirusTotal")

        // --- VirusTotal (análise automática)
        r("Análise automática de sites suspeitos", "Automatic scanning of suspicious sites", "Análisis automático de sitios sospechosos", "Analyse automatique des sites suspects")
        r("Cria uma conta gratuita em virustotal.com e cola aqui a tua chave de API (Perfil > API key). A chave fica só neste telemóvel.", "Create a free account at virustotal.com and paste your API key here (Profile > API key). The key stays only on this phone.", "Crea una cuenta gratuita en virustotal.com y pega aquí tu clave de API (Perfil > API key). La clave se queda solo en este teléfono.", "Créez un compte gratuit sur virustotal.com et collez ici votre clé d'API (Profil > API key). La clé reste uniquement sur ce téléphone.", "Crie uma conta gratuita em virustotal.com e cole aqui a sua chave de API (Perfil > API key). A chave fica só neste celular.")
        r("Chave de API do VirusTotal", "VirusTotal API key", "Clave de API de VirusTotal", "Clé d'API VirusTotal")
        r("Analisar automaticamente sites sem ligação segura", "Automatically scan sites without a secure connection", "Analizar automáticamente sitios sin conexión segura", "Analyser automatiquement les sites sans connexion sécurisée", "Analisar automaticamente sites sem conexão segura")
        r("Privacidade: o endereço analisado é enviado ao VirusTotal. Nada é enviado se não ativares a opção nem carregares em Analisar.", "Privacy: the scanned address is sent to VirusTotal. Nothing is sent unless you turn the option on or tap Scan.", "Privacidad: la dirección analizada se envía a VirusTotal. No se envía nada si no activas la opción ni pulsas Analizar.", "Confidentialité : l'adresse analysée est envoyée à VirusTotal. Rien n'est envoyé si vous n'activez pas l'option ni n'appuyez sur Analyser.", "Privacidade: o endereço analisado é enviado ao VirusTotal. Nada é enviado se você não ativar a opção nem tocar em Analisar.")
        r("Definições guardadas", "Settings saved", "Ajustes guardados", "Réglages enregistrés", "Configurações salvas")
        r("Analisar esta página", "Scan this page", "Analizar esta página", "Analyser cette page")
        r("A analisar no VirusTotal…", "Scanning with VirusTotal…", "Analizando en VirusTotal…", "Analyse avec VirusTotal…")
        r("Chave de API do VirusTotal inválida", "Invalid VirusTotal API key", "Clave de API de VirusTotal no válida", "Clé d'API VirusTotal invalide")
        r("Limite do VirusTotal atingido, tenta mais tarde", "VirusTotal limit reached, try again later", "Límite de VirusTotal alcanzado, inténtalo más tarde", "Limite VirusTotal atteinte, réessayez plus tard", "Limite do VirusTotal atingido, tente mais tarde")
        r("Não foi possível analisar o site", "Could not scan the site", "No se pudo analizar el sitio", "Impossible d'analyser le site")
        r("Nenhuma ameaça detetada ({} motores)", "No threats detected ({} engines)", "No se detectó ninguna amenaza ({} motores)", "Aucune menace détectée ({} moteurs)")
        r("⚠ Site suspeito", "⚠ Suspicious site", "⚠ Sitio sospechoso", "⚠ Site suspect")
        r("Sinalizado por", "Flagged by", "Señalado por", "Signalé par")
        r("{}/{} motores de segurança", "{}/{} security engines", "{}/{} motores de seguridad", "{}/{} moteurs de sécurité")
        r("Recomendamos sair deste site. Não introduzas palavras-passe nem dados pessoais.", "We recommend leaving this site. Do not enter passwords or personal data.", "Recomendamos salir de este sitio. No introduzcas contraseñas ni datos personales.", "Nous vous recommandons de quitter ce site. Ne saisissez ni mots de passe ni données personnelles.", "Recomendamos sair deste site. Não digite senhas nem dados pessoais.")
        r("Sair do site", "Leave site", "Salir del sitio", "Quitter le site")
        r("Ver relatório", "View report", "Ver informe", "Voir le rapport", "Ver relatório")
        r("Continuar por minha conta", "Continue at my own risk", "Continuar bajo mi responsabilidad", "Continuer à mes risques", "Continuar por minha conta")

        // --- descrições dos motores de pesquisa
        r("atual", "current", "actual", "actuel")
        r("Versão leve e sem distrações, mantém o anonimato", "Light, distraction-free version that keeps you anonymous", "Versión ligera y sin distracciones, mantiene el anonimato", "Version légère et sans distraction, préserve l'anonymat", "Versão leve e sem distrações, mantém o anonimato")
        r("Pesquisa privada que não te rastreia", "Private search that does not track you", "Búsqueda privada que no te rastrea", "Recherche privée qui ne vous suit pas", "Pesquisa privada que não rastreia você")
        r("Índice próprio e independente, focado na privacidade", "Independent index of its own, focused on privacy", "Índice propio e independiente, centrado en la privacidad", "Index propre et indépendant, axé sur la confidentialité")
        r("Resultados do Google de forma anónima", "Google results, anonymously", "Resultados de Google de forma anónima", "Résultats Google, de façon anonyme", "Resultados do Google de forma anônima")
        r("O mais usado; recolhe dados das pesquisas", "The most widely used; collects search data", "El más usado; recopila datos de las búsquedas", "Le plus utilisé ; collecte des données de recherche", "O mais usado; coleta dados das pesquisas")
        r("Resultados baseados no Google, sem rastreio", "Google-based results, no tracking", "Resultados basados en Google, sin rastreo", "Résultats basés sur Google, sans suivi")
        r("Resultados detalhados da Microsoft", "Detailed results from Microsoft", "Resultados detallados de Microsoft", "Résultats détaillés de Microsoft")
        r("Pesquisa suíça, boa para música (SoundCloud); exclui conteúdo malicioso e adulto", "Swiss search, good for music (SoundCloud); excludes malicious and adult content", "Búsqueda suiza, buena para música (SoundCloud); excluye contenido malicioso y para adultos", "Recherche suisse, idéale pour la musique (SoundCloud) ; exclut les contenus malveillants et adultes")

        // --- pressão longa (imagens, vídeos, links)
        r("Imagem", "Image", "Imagen", "Image")
        r("Vídeo", "Video", "Vídeo", "Vidéo")
        r("Áudio", "Audio", "Audio", "Audio")
        r("Link", "Link", "Enlace", "Lien")
        r("Guardar imagem", "Save image", "Guardar imagen", "Enregistrer l'image", "Salvar imagem")
        r("Guardar vídeo", "Save video", "Guardar vídeo", "Enregistrer la vidéo", "Salvar vídeo")
        r("Guardar áudio", "Save audio", "Guardar audio", "Enregistrer l'audio", "Salvar áudio")
        r("Transferências", "Downloads", "Descargas", "Téléchargements", "Downloads")
        r("Copiar link do ficheiro", "Copy file link", "Copiar enlace del archivo", "Copier le lien du fichier", "Copiar link do arquivo")
        r("Abrir ficheiro", "Open file", "Abrir archivo", "Ouvrir le fichier", "Abrir arquivo")
        r("Abrir link", "Open link", "Abrir enlace", "Ouvrir le lien")
        r("Partilhar link", "Share link", "Compartir enlace", "Partager le lien", "Compartilhar link")
        r("Link copiado", "Link copied", "Enlace copiado", "Lien copié")
        r("Este conteúdo não pode ser guardado (transmissão protegida ou em blocos)", "This content cannot be saved (protected or segmented stream)", "Este contenido no se puede guardar (transmisión protegida o por bloques)", "Ce contenu ne peut pas être enregistré (flux protégé ou segmenté)", "Este conteúdo não pode ser salvo (transmissão protegida ou em blocos)")

        // --- histórico
        r("Histórico", "History", "Historial", "Historique")
        r("Guardar histórico", "Save history", "Guardar historial", "Enregistrer l'historique", "Salvar histórico")
        r("Não guarda no modo privado", "Not saved in private mode", "No se guarda en modo privado", "Non enregistré en mode privé", "Não salva no modo privado")
        r("{} páginas", "{} pages", "{} páginas", "{} pages")
        r("Histórico ligado", "History on", "Historial activado", "Historique activé", "Histórico ativado")
        r("Histórico desligado", "History off", "Historial desactivado", "Historique désactivé", "Histórico desativado")
        r("Ainda não há páginas no histórico.", "There are no pages in your history yet.", "Aún no hay páginas en el historial.", "Aucune page dans l'historique pour l'instant.", "Ainda não há páginas no histórico.")
        r("A mostrar as 60 mais recentes.", "Showing the 60 most recent.", "Mostrando las 60 más recientes.", "Affichage des 60 plus récentes.", "Mostrando as 60 mais recentes.")
        r("Limpar histórico", "Clear history", "Borrar historial", "Effacer l'historique")
        r("Histórico apagado", "History cleared", "Historial borrado", "Historique effacé")
        r("🕘  Histórico", "🕘  History", "🕘  Historial", "🕘  Historique")

        // --- separadores
        r("Separadores", "Tabs", "Pestañas", "Onglets", "Abas")
        r("Novo separador", "New tab", "Nueva pestaña", "Nouvel onglet", "Nova aba")
        r("1 separador", "1 tab", "1 pestaña", "1 onglet", "1 aba")
        r("{} separadores", "{} tabs", "{} pestañas", "{} onglets", "{} abas")
        r("Limite de separadores atingido", "Tab limit reached", "Límite de pestañas alcanzado", "Limite d'onglets atteinte", "Limite de abas atingido")

        // --- deteção de media
        r("Media desta página", "Media on this page", "Contenido multimedia de esta página", "Médias de cette page", "Mídia desta página")
        r("1 ficheiro", "1 file", "1 archivo", "1 fichier", "1 arquivo")
        r("{} ficheiros", "{} files", "{} archivos", "{} fichiers", "{} arquivos")
        r("Sem media detetada nesta página.", "No media detected on this page.", "No se detectó contenido multimedia en esta página.", "Aucun média détecté sur cette page.", "Nenhuma mídia detectada nesta página.")
        r("A mostrar os 60 primeiros.", "Showing the first 60.", "Mostrando los primeros 60.", "Affichage des 60 premiers.", "Mostrando os 60 primeiros.")
        r("Toca num ficheiro para o guardar em Transferências. Vídeos em blocos ou protegidos (YouTube, streaming) não aparecem.", "Tap a file to save it to Downloads. Segmented or protected videos (YouTube, streaming) do not appear.", "Toca un archivo para guardarlo en Descargas. Los vídeos por bloques o protegidos (YouTube, streaming) no aparecen.", "Touchez un fichier pour l'enregistrer dans Téléchargements. Les vidéos segmentées ou protégées (YouTube, streaming) n'apparaissent pas.", "Toque num arquivo para salvá-lo em Downloads. Vídeos em blocos ou protegidos (YouTube, streaming) não aparecem.")

        // --- pasta de downloads
        r("Pasta", "Folder", "Carpeta", "Dossier")
        r("Pasta de downloads", "Download folder", "Carpeta de descargas", "Dossier de téléchargement")
        r("Onde os ficheiros são guardados", "Where files are saved", "Dónde se guardan los archivos", "Où les fichiers sont enregistrés", "Onde os arquivos são salvos")
        r("Pasta predefinida do Android", "Android's default folder", "Carpeta predeterminada de Android", "Dossier par défaut d'Android", "Pasta padrão do Android")
        r("Pasta escolhida", "Chosen folder", "Carpeta elegida", "Dossier choisi", "Pasta escolhida")
        r("Escolher outra pasta…", "Choose another folder…", "Elegir otra carpeta…", "Choisir un autre dossier…", "Escolher outra pasta…")
        r("Qualquer pasta do telemóvel ou do cartão", "Any folder on the phone or the card", "Cualquier carpeta del teléfono o de la tarjeta", "N'importe quel dossier du téléphone ou de la carte", "Qualquer pasta do celular ou do cartão")
        r("Numa pasta escolhida a app guarda sem notificação de progresso (aparece um aviso quando termina). Só ligações https.", "In a chosen folder the app saves without a progress notification (a message appears when it finishes). HTTPS links only.", "En una carpeta elegida la app guarda sin notificación de progreso (aparece un aviso al terminar). Solo enlaces https.", "Dans un dossier choisi, l'app enregistre sans notification de progression (un message apparaît à la fin). Liens https uniquement.", "Numa pasta escolhida o app salva sem notificação de progresso (aparece um aviso ao terminar). Só links https.")
        r("Pasta: Transferências", "Folder: Downloads", "Carpeta: Descargas", "Dossier : Téléchargements")
        r("Pasta de downloads: {}", "Download folder: {}", "Carpeta de descargas: {}", "Dossier de téléchargement : {}")
        r("Não foi possível abrir o seletor de pastas", "Could not open the folder picker", "No se pudo abrir el selector de carpetas", "Impossible d'ouvrir le sélecteur de dossiers")
        r("Não foi possível usar essa pasta", "Could not use that folder", "No se pudo usar esa carpeta", "Impossible d'utiliser ce dossier")
        r("A guardar…", "Saving…", "Guardando…", "Enregistrement…")
        r("A guardar: {}", "Saving: {}", "Guardando: {}", "Enregistrement : {}", "Salvando: {}")
        r("Transferência não suportada (só ligações https)", "Download not supported (https links only)", "Descarga no compatible (solo enlaces https)", "Téléchargement non pris en charge (liens https uniquement)", "Download não suportado (só links https)")
        r("A página continua a falhar", "The page keeps failing", "La página sigue fallando", "La page continue de planter", "A página continua falhando")
        r("Utilizador", "Username", "Usuario", "Nom d'utilisateur", "Usuário")
        r("Palavra-passe", "Password", "Contraseña", "Mot de passe", "Senha")
        r("Permissão pedida", "Permission requested", "Permiso solicitado", "Autorisation demandée", "Permissão solicitada")
        r("Permitir", "Allow", "Permitir", "Autoriser")
        r("Bloquear", "Block", "Bloquear", "Bloquer")
        r("{} quer saber a tua localização", "{} wants to know your location", "{} quiere saber tu ubicación", "{} souhaite connaître votre position", "{} quer saber sua localização")
        r("{} quer usar a câmara", "{} wants to use the camera", "{} quiere usar la cámara", "{} souhaite utiliser la caméra", "{} quer usar a câmera")
        r("{} quer usar o microfone", "{} wants to use the microphone", "{} quiere usar el micrófono", "{} souhaite utiliser le microphone")
        r("{} quer usar a câmara e o microfone", "{} wants to use the camera and microphone", "{} quiere usar la cámara y el micrófono", "{} souhaite utiliser la caméra et le microphone", "{} quer usar a câmera e o microfone")
        r("🚨 Ficheiro perigoso", "🚨 Dangerous file", "🚨 Archivo peligroso", "🚨 Fichier dangereux", "🚨 Arquivo perigoso")
        r("⚠ Ficheiro suspeito", "⚠ Suspicious file", "⚠ Archivo sospechoso", "⚠ Fichier suspect", "⚠ Arquivo suspeito")
        r("Extensão", "Extension", "Extensión", "Extension")
        r("(nenhuma)", "(none)", "(ninguna)", "(aucune)")
        r("Conteúdo real", "Actual content", "Contenido real", "Contenu réel", "Conteúdo real")
        r("vazio (0 bytes)", "empty (0 bytes)", "vacío (0 bytes)", "vide (0 octet)")
        r("Pode ser um programa disfarçado de outro ficheiro. Recomendamos apagar.", "It may be a program disguised as another file. We recommend deleting it.", "Puede ser un programa disfrazado de otro archivo. Recomendamos borrarlo.", "Il peut s'agir d'un programme déguisé en autre fichier. Nous recommandons de le supprimer.", "Pode ser um programa disfarçado de outro arquivo. Recomendamos apagar.")
        r("O ficheiro não corresponde à extensão. Pode estar corrompido, ser uma página de erro ou estar disfarçado.", "The file does not match its extension. It may be corrupted, an error page or disguised.", "El archivo no coincide con su extensión. Puede estar dañado, ser una página de error o estar disfrazado.", "Le fichier ne correspond pas à son extension. Il peut être corrompu, être une page d'erreur ou être déguisé.", "O arquivo não corresponde à extensão. Pode estar corrompido, ser uma página de erro ou estar disfarçado.")
        r("Apagar", "Delete", "Borrar", "Supprimer")
        r("Manter", "Keep", "Conservar", "Conserver")
        r("Ficheiro apagado", "File deleted", "Archivo borrado", "Fichier supprimé", "Arquivo apagado")
        r("Guardado: {}", "Saved: {}", "Guardado: {}", "Enregistré : {}", "Salvo: {}")
        r("Pasta indisponível: a guardar em Transferências", "Folder unavailable: saving to Downloads", "Carpeta no disponible: guardando en Descargas", "Dossier indisponible : enregistrement dans Téléchargements", "Pasta indisponível: salvando em Downloads")

        // --- idioma
        r("Escolhe o idioma da app", "Choose the app language", "Elige el idioma de la app", "Choisissez la langue de l'app", "Escolha o idioma do app")
        r("Automático (idioma do sistema)", "Automatic (system language)", "Automático (idioma del sistema)", "Automatique (langue du système)", "Automático (idioma do sistema)")

        // --- página inicial
        r("Aberta ao iniciar a app e no botão Início", "Opens when the app starts and with the Home button", "Se abre al iniciar la app y con el botón Inicio", "S'ouvre au démarrage de l'app et avec le bouton Accueil")
        r("ex.: https://exemplo.pt  (vazio = página do motor)", "e.g. https://example.com  (empty = search engine page)", "p. ej.: https://ejemplo.es  (vacío = página del buscador)", "ex. : https://exemple.fr  (vide = page du moteur)")
        r("Página inicial guardada", "Home page saved", "Página de inicio guardada", "Page d'accueil enregistrée", "Página inicial salva", "Homepage saved")
        r("Usar a página atual", "Use current page", "Usar la página actual", "Utiliser la page actuelle")
        r("Página inicial definida", "Home page set", "Página de inicio definida", "Page d'accueil définie", us = "Homepage set")
        r("Abre primeiro uma página", "Open a page first", "Abre primero una página", "Ouvrez d'abord une page", "Abra primeiro uma página")

        // --- permissões de extensões
        r("Adicionar \"{}\"?", "Add \"{}\"?", "¿Añadir \"{}\"?", "Ajouter « {} » ?")
        r("Revê as permissões pedidas", "Review the permissions requested", "Revisa los permisos solicitados", "Vérifiez les autorisations demandées", "Revise as permissões solicitadas")
        r("Não pede permissões especiais.", "It asks for no special permissions.", "No pide permisos especiales.", "Aucune autorisation particulière demandée.")
        r("Permissões", "Permissions", "Permisos", "Autorisations")
        r("Sites a que acede", "Sites it can access", "Sitios a los que accede", "Sites auxquels elle accède", "Sites que acessa")

        // --- barra e sessão
        r("🕶 Privado — pesquisar ou escrever endereço", "🕶 Private — search or type address", "🕶 Privado — buscar o escribir dirección", "🕶 Privé — rechercher ou saisir une adresse")
        r("Pesquisar ou escrever endereço", "Search or type address", "Buscar o escribir dirección", "Rechercher ou saisir une adresse")
        r("Modo privado ligado", "Private mode on", "Modo privado activado", "Mode privé activé", "Modo privado ativado")
        r("Modo privado desligado", "Private mode off", "Modo privado desactivado", "Mode privé désactivé", "Modo privado desativado")
        r("Página recuperada após falha", "Page recovered after a crash", "Página recuperada tras un fallo", "Page récupérée après un plantage")
        r("Pop-up bloqueado", "Pop-up blocked", "Ventana emergente bloqueada", "Pop-up bloqué")
        r("Ligação externa bloqueada", "External link blocked", "Enlace externo bloqueado", "Lien externe bloqué", "Link externo bloqueado")
        r("Redirecionamento bloqueado", "Redirect blocked", "Redirección bloqueada", "Redirection bloquée")

        // --- downloads, partilha
        r("Transferência não suportada", "Download not supported", "Descarga no compatible", "Téléchargement non pris en charge", "Download não suportado")
        r("A transferir: {}", "Downloading: {}", "Descargando: {}", "Téléchargement : {}", "Baixando: {}")
        r("Falhou a transferência", "Download failed", "Falló la descarga", "Échec du téléchargement", "Falha no download")
        r("Escolher ficheiro", "Choose file", "Elegir archivo", "Choisir un fichier", "Escolher arquivo")
        r("Nada para partilhar", "Nothing to share", "Nada que compartir", "Rien à partager", "Nada para compartilhar")
        r("Nada para copiar", "Nothing to copy", "Nada que copiar", "Rien à copier")
        r("Link copiado sem parâmetros de rastreio", "Link copied without tracking parameters", "Enlace copiado sin parámetros de rastreo", "Lien copié sans paramètres de suivi")

        // --- limpar dados
        r("Escolhe o que queres apagar", "Choose what to delete", "Elige qué quieres borrar", "Choisissez ce que vous voulez effacer", "Escolha o que quer apagar")
        r("🗂  Cache (ficheiros temporários e imagens)", "🗂  Cache (temporary files and images)", "🗂  Caché (archivos temporales e imágenes)", "🗂  Cache (fichiers temporaires et images)", "🗂  Cache (arquivos temporários e imagens)")
        r("💾  Dados de sites (armazenamento local)", "💾  Site data (local storage)", "💾  Datos de sitios (almacenamiento local)", "💾  Données des sites (stockage local)")
        r("🔐  Permissões e definições de sites", "🔐  Site permissions and settings", "🔐  Permisos y ajustes de sitios", "🔐  Autorisations et réglages des sites", "🔐  Permissões e configurações de sites")
        r("★  Favoritos", "★  Bookmarks", "★  Favoritos", "★  Favoris")
        r("Favoritos apagados", "Bookmarks deleted", "Favoritos borrados", "Favoris supprimés")
        r("Nada selecionado", "Nothing selected", "Nada seleccionado", "Rien de sélectionné")
        r("Dados apagados", "Data deleted", "Datos borrados", "Données effacées")
        r("Falhou a limpeza", "Clearing failed", "Falló el borrado", "Échec de l'effacement", "Falha na limpeza")
        r("Sem site para limpar", "No site to clear", "Ningún sitio que borrar", "Aucun site à effacer")
        r("Dados de {} apagados", "Data for {} deleted", "Datos de {} borrados", "Données de {} effacées")
        r("Falhou a limpeza de {}", "Failed to clear {}", "No se pudo borrar {}", "Impossible d'effacer {}", "Falha ao limpar {}")

        // --- proteção
        r("O que está a proteger-te nesta página", "What is protecting you on this page", "Lo que te protege en esta página", "Ce qui vous protège sur cette page", "O que está protegendo você nesta página")
        r("Ligação", "Connection", "Conexión", "Connexion", "Conexão")
        r("Segura (HTTPS)", "Secure (HTTPS)", "Segura (HTTPS)", "Sécurisée (HTTPS)")
        r("Sem página aberta", "No page open", "Sin página abierta", "Aucune page ouverte")
        r("Rastreadores bloqueados", "Trackers blocked", "Rastreadores bloqueados", "Traqueurs bloqués")
        r("{} nesta página", "{} on this page", "{} en esta página", "{} sur cette page")
        r("Anúncios (uBlock Origin)", "Ads (uBlock Origin)", "Anuncios (uBlock Origin)", "Publicités (uBlock Origin)")
        r("Ativo", "Active", "Activo", "Actif")
        r("Não carregado", "Not loaded", "No cargado", "Non chargé")
        r("Pop-ups e redirecionamentos", "Pop-ups and redirects", "Ventanas emergentes y redirecciones", "Pop-ups et redirections")
        r("Bloqueados sem clique", "Blocked without a click", "Bloqueados sin clic", "Bloqués sans clic")
        r("Do sistema", "System", "Del sistema", "Du système")
        r("Limpar dados do site", "Clear site data", "Borrar datos del sitio", "Effacer les données du site")

        // --- motor de pesquisa e DNS
        r("Motor de pesquisa", "Search engine", "Buscador", "Moteur de recherche")
        r("Usado na barra de endereço", "Used in the address bar", "Se usa en la barra de direcciones", "Utilisé dans la barre d'adresse")
        r("{}  ·  atual", "{}  ·  current", "{}  ·  actual", "{}  ·  actuel")
        r("Motor de pesquisa: {}", "Search engine: {}", "Buscador: {}", "Moteur de recherche : {}")
        r("Servidor DNS (DoH)", "DNS server (DoH)", "Servidor DNS (DoH)", "Serveur DNS (DoH)")
        r("Usado só nesta app. Aplica-se após reiniciar.", "Used only in this app. Applies after restart.", "Se usa solo en esta app. Se aplica tras reiniciar.", "Utilisé uniquement dans cette app. S'applique après redémarrage.")
        r("https://freedns.controld.com/p2  (vazio = DNS do sistema)", "https://freedns.controld.com/p2  (empty = system DNS)", "https://freedns.controld.com/p2  (vacío = DNS del sistema)", "https://freedns.controld.com/p2  (vide = DNS du système)")
        r("Predefinido: Control D p2 (bloqueia malware e anúncios).", "Default: Control D p2 (blocks malware and ads).", "Predeterminado: Control D p2 (bloquea malware y anuncios).", "Par défaut : Control D p2 (bloque les logiciels malveillants et les publicités).", "Padrão: Control D p2 (bloqueia malware e anúncios).")
        r("Guardar e reiniciar", "Save and restart", "Guardar y reiniciar", "Enregistrer et redémarrer", "Salvar e reiniciar")
        r("Repor p2", "Reset to p2", "Restablecer p2", "Rétablir p2", "Restaurar p2")
        r("A reiniciar para aplicar o DNS…", "Restarting to apply DNS…", "Reiniciando para aplicar el DNS…", "Redémarrage pour appliquer le DNS…", "Reiniciando para aplicar o DNS…")

        // --- favoritos
        r("Removido dos favoritos", "Removed from bookmarks", "Eliminado de favoritos", "Retiré des favoris")
        r("Adicionado aos favoritos", "Added to bookmarks", "Añadido a favoritos", "Ajouté aux favoris")
        r("{} guardado(s)", "{} saved", "{} guardado(s)", "{} enregistré(s)", "{} salvo(s)")
        r("Ainda não tens favoritos.", "You have no bookmarks yet.", "Aún no tienes favoritos.", "Vous n'avez pas encore de favoris.", "Você ainda não tem favoritos.")
        r("Abre uma página e usa Menu > Favorito.", "Open a page and use Menu > Bookmark.", "Abre una página y usa Menú > Favorito.", "Ouvrez une page et utilisez Menu > Favori.", "Abra uma página e use Menu > Favorito.")

        // --- extensões
        r("Não foi possível listar as extensões", "Could not list extensions", "No se pudieron listar las extensiones", "Impossible de lister les extensions")
        r("{} instalada(s)", "{} installed", "{} instalada(s)", "{} installée(s)")
        r("Nenhuma extensão instalada.", "No extensions installed.", "No hay extensiones instaladas.", "Aucune extension installée.")
        r("Embutida", "Built-in", "Integrada", "Intégrée")
        r("Instalada", "Installed", "Instalada", "Installée")
        r("Embutida · v{}", "Built-in · v{}", "Integrada · v{}", "Intégrée · v{}")
        r("Instalada · v{}", "Installed · v{}", "Instalada · v{}", "Installée · v{}")
        r("Ativada", "Enabled", "Activada", "Activée")
        r("Desativada", "Disabled", "Desactivada", "Désactivée")
        r("Falhou: {}", "Failed: {}", "Falló: {}", "Échec : {}", "Falha: {}")
        r("Extensão removida", "Extension removed", "Extensión eliminada", "Extension supprimée")
        r("Adicionar extensão", "Add extension", "Añadir extensión", "Ajouter une extension")
        r("Escolhe a origem", "Choose the source", "Elige el origen", "Choisissez la source", "Escolha a origem")
        r("Loja de extensões", "Extension store", "Tienda de extensiones", "Boutique d'extensions")
        r("addons.mozilla.org · toca no link de descarga (.xpi)", "addons.mozilla.org · tap the download link (.xpi)", "addons.mozilla.org · toca el enlace de descarga (.xpi)", "addons.mozilla.org · touchez le lien de téléchargement (.xpi)", "addons.mozilla.org · toque no link de download (.xpi)")
        r("Abre a extensão e toca no link de descarga (.xpi) para a instalar", "Open the extension and tap the download link (.xpi) to install it", "Abre la extensión y toca el enlace de descarga (.xpi) para instalarla", "Ouvrez l'extension et touchez le lien de téléchargement (.xpi) pour l'installer", "Abra a extensão e toque no link de download (.xpi) para instalá-la")
        r("Proteção contra malware e phishing", "Protection against malware and phishing", "Protección contra malware y phishing", "Protection contre les logiciels malveillants et le phishing")
        r("Instalar por link", "Install from link", "Instalar desde enlace", "Installer depuis un lien")
        r("Cola um link .xpi", "Paste an .xpi link", "Pega un enlace .xpi", "Collez un lien .xpi", "Cole um link .xpi")
        r("Instalar extensão?", "Install extension?", "¿Instalar la extensión?", "Installer l'extension ?")
        r("De addons.mozilla.org", "From addons.mozilla.org", "De addons.mozilla.org", "Depuis addons.mozilla.org")
        r("Descarregado de addons.mozilla.org", "Downloaded from addons.mozilla.org", "Descargado de addons.mozilla.org", "Téléchargé depuis addons.mozilla.org", "Baixado de addons.mozilla.org")
        r(
            "Já tens o uBlock Origin: se ambos bloquearem anúncios pode haver sobreposição. Desativa o bloqueio de anúncios no Browser Guard e mantém a proteção contra malware e phishing.",
            "You already have uBlock Origin: if both block ads they may overlap. Turn off ad blocking in Browser Guard and keep the malware and phishing protection.",
            "Ya tienes uBlock Origin: si ambos bloquean anuncios puede haber solapamiento. Desactiva el bloqueo de anuncios en Browser Guard y mantén la protección contra malware y phishing.",
            "Vous avez déjà uBlock Origin : si les deux bloquent les publicités, ils peuvent se chevaucher. Désactivez le blocage des publicités dans Browser Guard et gardez la protection contre les logiciels malveillants et le phishing.",
            "Você já tem o uBlock Origin: se ambos bloquearem anúncios pode haver sobreposição. Desative o bloqueio de anúncios no Browser Guard e mantenha a proteção contra malware e phishing."
        )
        r("A compatibilidade com o Android não está confirmada.", "Compatibility with Android is not confirmed.", "La compatibilidad con Android no está confirmada.", "La compatibilité avec Android n'est pas confirmée.")
        r("Link direto de um ficheiro .xpi", "Direct link to an .xpi file", "Enlace directo a un archivo .xpi", "Lien direct vers un fichier .xpi", "Link direto de um arquivo .xpi")
        r("Instala só extensões em que confias; de preferência de addons.mozilla.org.", "Only install extensions you trust, preferably from addons.mozilla.org.", "Instala solo extensiones de confianza, preferiblemente de addons.mozilla.org.", "N'installez que des extensions de confiance, de préférence depuis addons.mozilla.org.", "Instale só extensões em que você confia, de preferência de addons.mozilla.org.")
        r("Link inválido: tem de ser https e terminar em .xpi", "Invalid link: it must be https and end in .xpi", "Enlace no válido: debe ser https y terminar en .xpi", "Lien invalide : il doit être en https et finir par .xpi", "Link inválido: precisa ser https e terminar em .xpi")
        r("A descarregar extensão…", "Downloading extension…", "Descargando extensión…", "Téléchargement de l'extension…", "Baixando extensão…")
        r("Instalada: extensão", "Installed: extension", "Instalada: extensión", "Installée : extension")
        r("Instalada: {}", "Installed: {}", "Instalada: {}", "Installée : {}")

        // --- sobre
        r("Versão {}", "Version {}", "Versión {}", "Version {}")
        r(
            "Browser leve e privado sobre GeckoView (motor do Firefox). Em homenagem à nau São Gabriel, nau-capitânia de Vasco da Gama na viagem à Índia (1497–1499): um browser também é um explorador.",
            "A light, private browser built on GeckoView (the Firefox engine). Named after the São Gabriel, Vasco da Gama's flagship on the voyage to India (1497–1499): a browser is an explorer too.",
            "Navegador ligero y privado sobre GeckoView (el motor de Firefox). En homenaje a la nao São Gabriel, nave capitana de Vasco da Gama en el viaje a la India (1497–1499): un navegador también es un explorador.",
            "Navigateur léger et privé basé sur GeckoView (le moteur de Firefox). En hommage au São Gabriel, nef amirale de Vasco de Gama lors du voyage vers les Indes (1497–1499) : un navigateur est aussi un explorateur."
        )
        r("DNS sobre HTTPS", "DNS over HTTPS", "DNS sobre HTTPS", "DNS sur HTTPS")
        r("Só nesta app", "Only in this app", "Solo en esta app", "Uniquement dans cette app")
        r("Proteção estrita", "Strict protection", "Protección estricta", "Protection stricte")
        r("Rastreio e só HTTPS", "Tracking and HTTPS-only", "Rastreo y solo HTTPS", "Suivi et HTTPS uniquement")
        r("Embutido", "Built-in", "Integrado", "Intégré")
        r("Embutido · v{}", "Built-in · v{}", "Integrado · v{}", "Intégré · v{}")
        r("Bloqueados", "Blocked", "Bloqueados", "Bloqués")
        r("Mantém o motor atualizado: a segurança depende disso.", "Keep the engine up to date: security depends on it.", "Mantén el motor actualizado: la seguridad depende de ello.", "Gardez le moteur à jour : la sécurité en dépend.", "Mantenha o motor atualizado: a segurança depende disso.")
    }
}
