import com.android.build.api.variant.FilterConfiguration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// `versionCode` de base, celui de l'application. Il s'incrémente à chaque publication, et lui seul.
val baseVersionCode = 1

// Convention de `versionCode` par ABI — **à ne pas changer une fois l'application publiée.**
//
// Android et F-Droid exigent que deux APK d'une même application n'aient pas le même `versionCode`
// sur des ABI différentes : c'est ce nombre, et lui seul, qui départage les fichiers candidats.
// Le `versionCode` publié vaut donc `rang de l'ABI × 1000 + baseVersionCode` — 1001, 2001, 3001 et
// 4001 pour la version 1.
//
// Les rangs ne sont pas arbitraires : **une ABI doit avoir un rang supérieur à toutes celles qu'un
// appareil qui la porte sait également exécuter**, sans quoi l'appareil installerait la mauvaise.
// Un appareil `arm64-v8a` exécute aussi `armeabi-v7a`, un appareil `x86_64` exécute aussi `x86`, et
// un appareil x86 exécute souvent `armeabi-v7a` par traduction : d'où cet ordre, qui est celui
// recommandé par la documentation Android.
//
// Le multiplicateur 1000 laisse 999 publications avant que deux rangs se rejoignent, et garde le
// `versionCode` lisible : le rang se lit à gauche, la version de l'application à droite.
val abiVersionCodeRanks = mapOf(
  "armeabi-v7a" to 1,
  "arm64-v8a" to 2,
  "x86" to 3,
  "x86_64" to 4,
)

val abiVersionCodeMultiplier = 1000

// La propriété qui restreint la compilation à **une seule** ABI : `./gradlew :app:assembleRelease
// -Pabi=arm64-v8a`. Elle existe pour la recette de compilation de F-Droid (docs/fdroid.md § 6).
//
// `fdroid build` cherche **un** APK par bloc `Builds:` et échoue s'il en trouve plusieurs
// (`BuildException('More than one resulting apks found in …')`). Le découpage par ABI ci-dessous
// en produit quatre d'un coup : la recette demande donc chaque architecture séparément, par quatre
// blocs `Builds:` qui ne diffèrent que par leur `gradleprops:` et leur `versionCode`.
//
// **Sans la propriété, rien ne change** : les quatre APK sont produits comme avant, et la CI comme
// les commandes de CLAUDE.md restent valables. La propriété ne touche qu'à la *liste des ABI
// produites* ; elle ne touche **jamais** au `versionCode`, qui reste celui du rang de l'ABI, à
// l'identique dans les deux modes. Un `versionCode` qui dépendrait du mode de compilation serait
// un incident de publication.
val requestedAbi = providers.gradleProperty("abi").orNull?.trim()?.takeIf { it.isNotEmpty() }

val includedAbis: List<String> = when (requestedAbi) {
  null -> abiVersionCodeRanks.keys.toList()

  in abiVersionCodeRanks.keys -> listOf(requestedAbi)

  // Une valeur inconnue échoue tout de suite, et nommément : sans cela, `include()` ne retiendrait
  // aucune ABI et la compilation rendrait zéro APK sans rien dire, ce qu'une recette F-Droid
  // signalerait beaucoup plus loin et beaucoup moins clairement.
  else -> error(
    "Propriété -Pabi inconnue : « $requestedAbi ». " +
      "Valeurs acceptées : ${abiVersionCodeRanks.keys.joinToString(", ")}. " +
      "Sans -Pabi, les quatre APK d'architecture sont produits.",
  )
}

/**
 * Le type de compilation de **mesure** : la publication minifiée, signée avec la clé de débogage.
 * Il n'est jamais publié ; il n'existe que pour que le § 2 et le § 5.7 de la spec soient vérifiables
 * sur un appareil. Voir le bloc `buildTypes` et CLAUDE.md, § « Mesurer la publication ».
 */
val measurementBuildType = "releaseTest"

/** L'objectif de SPEC.md § 2 : « APK visé < 15 Mo **par APK d'architecture** ». */
val apkSizeBudgetBytes = 15L * 1000 * 1000

android {
  namespace = "io.github.mgdx.escale"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    // Figé : il ne pourra plus changer une fois l'application publiée sur F-Droid (SPEC.md § 1.1).
    applicationId = "io.github.mgdx.escale"
    minSdk = 26
    targetSdk = 37
    versionCode = baseVersionCode
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // **Aucune clé de signature, aucun mot de passe, aucun fichier de clés dans le dépôt** (SPEC.md
  // § 2 et § 11) : F-Droid compile depuis les sources et signe lui-même l'APK qu'il publie. Le type
  // `release` n'a donc **volontairement aucune `signingConfig`** — `assembleRelease` produit des
  // APK non signés, ce qui est exactement ce que F-Droid attend, et le résultat ne dépend d'aucun
  // fichier propre à la machine qui compile. C'est la condition de la compilation reproductible.
  //
  // Le seul jeu de clés utilisé ici est celui de **débogage**, engendré par le SDK Android dans
  // `~/.android/debug.keystore`, hors du dépôt, et réservé à la variante de mesure ci-dessous.
  buildTypes {
    release {
      // R8 : minification du code **et** réduction des ressources. En AGP 9, `optimization.enable`
      // remplace le couple `isMinifyEnabled` / `isShrinkResources` et active les deux d'un coup ;
      // le fichier de règles par défaut `proguard-android-optimize.txt` est inclus d'office
      // (`optimization.keepRules.includeDefault`, vrai par convention).
      //
      // Les règles propres au projet vivent dans `src/main/keepRules/` — le mécanisme d'AGP 9 —
      // et non dans un `proguard-rules.pro` référencé par `proguardFiles`, qui est l'ancienne voie.
      optimization {
        enable = true
      }
    }

    // La publication, **signée avec la clé de débogage**, pour pouvoir être installée et mesurée.
    //
    // `assembleRelease` produit des APK non signés : ils sont impossibles à installer, donc
    // impossibles à mesurer, alors que SPEC.md § 2 et § 5.7 exigent des critères « vérifiés, pas
    // seulement souhaités ». D'où cette variante, qui reprend **exactement** la configuration R8 de
    // `release` et n'y ajoute qu'une signature.
    //
    // Pourquoi une variante à part plutôt qu'une `signingConfig` posée sur `release` avec repli sur
    // la clé de débogage : ce repli ferait dépendre l'artefact publié d'un fichier propre à la
    // machine (`~/.android/debug.keystore`) et laisserait une signature v1 dans le `META-INF` de
    // l'APK que F-Droid re-signe ensuite. Ici, l'artefact `release` que F-Droid compile n'est
    // touché par rien de tout cela.
    create(measurementBuildType) {
      initWith(getByName("release"))
      // Reposé explicitement : `initWith` copie les propriétés du type de compilation, et faire
      // reposer l'égalité des deux variantes sur ce détail rendrait la mesure fausse le jour où
      // elle changerait.
      optimization {
        enable = true
      }
      signingConfig = signingConfigs.getByName("debug")
      // `:data` ne connaît que `debug` et `release` : sans ce repli, la résolution de dépendance
      // échoue faute de variante `releaseTest` côté bibliothèque.
      matchingFallbacks += "release"
      // Traçable par Perfetto et par le profileur sans être `debuggable` : c'est ce qui permet de
      // mesurer le démarrage sur un binaire identique à celui qui sera publié (SPEC.md § 5.7).
      isProfileable = true
    }
  }

  // Un APK par architecture (SPEC.md § 2 : moins de 15 Mo). Les bibliothèques natives de MapLibre
  // pèsent à elles seules une quarantaine de mégaoctets une fois les quatre architectures réunies ;
  // aucun appareil n'en exécute plus d'une. F-Droid sert à chaque appareil le fichier qui lui
  // convient, et les variantes x86 restent produites pour les émulateurs.
  splits {
    abi {
      isEnable = true
      reset()
      // La liste complète, ou la seule ABI demandée par `-Pabi` (voir plus haut). Les quatre noms
      // sont écrits en toutes lettres dans la branche par défaut : c'est là que lint les lit pour
      // sa vérification `ChromeOsAbiSupport`, qui exige un binaire x86 ou x86_64.
      if (requestedAbi == null) {
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      } else {
        include(requestedAbi)
      }
      // Pas d'APK universel : il annulerait tout le gain, et personne ne l'installerait.
      isUniversalApk = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    // Le `User-Agent` reprend le `versionName` : il se lit dans BuildConfig, jamais en dur
    // (SPEC.md § 4.2).
    buildConfig = true
  }

  lint {
    // Un avertissement de lint est une erreur (CLAUDE.md).
    warningsAsErrors = true
    abortOnError = true
    // `checkDependencies` n'est pas activé : lint ne sait pas analyser un module Kotlin/JVM comme
    // :core et le signale par un avertissement à chaque exécution. Chaque module Android porte donc
    // sa propre tâche lint, et :core est couvert par ktlint, detekt et ses tests JVM.
  }
}

// Applique la convention ci-dessus à chaque APK produit. La valeur est posée sur la sortie, jamais
// sur `defaultConfig` : `defaultConfig.versionCode` reste le numéro de version de l'application.
androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters
        .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
        ?.identifier
        ?: return@forEach
      val rank = abiVersionCodeRanks[abi] ?: error("ABI sans rang de versionCode : $abi")
      output.versionCode.set(rank * abiVersionCodeMultiplier + baseVersionCode)
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    // Un avertissement du compilateur est une erreur (CLAUDE.md).
    allWarningsAsErrors.set(true)
  }
}

dependencies {
  implementation(project(":core"))
  implementation(project(":data"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // `SavedStateHandle` : le seul état qu'Android restitue après avoir tué le processus en
  // arrière-plan. Sans lui, la saisie de l'écran « Serveur MOTIS » est perdue.
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)

  // Carte : MapLibre GL Android, licence BSD, compatible F-Droid (SPEC.md § 3). La carte est
  // rendue par le moteur natif, jamais par une WebView (SPEC.md § 2).
  implementation(libs.maplibre.android.sdk)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------------------------
// Garde-fous de la publication minifiée.
//
// R8 ne tourne **que** sur la variante de publication : aucun test JVM, aucun `lint`, aucun
// `detekt` ne voit son résultat. Une règle de conservation manquante ne casse donc pas la
// compilation — elle casse l'application à l'exécution, en publication seulement, et silencieusement.
// Ces deux tâches sont ce qui rend le défaut détectable sans appareil ; elles finalisent
// `assembleRelease`, donc elles s'exécutent d'office.
// ---------------------------------------------------------------------------------------------

/**
 * Les classes que **quelque chose d'autre que le bytecode** désigne par leur nom : le code natif de
 * MapLibre (`FindClass`), `Class.forName` dans Room, le manifeste pour l'application
 * et l'activité. R8 n'a aucun moyen de le savoir : si la règle qui les protège disparaît, il les
 * renomme, et l'application se lance puis échoue à l'endroit exact où personne ne regarde.
 *
 * Chaque entrée a été établie en lisant `outputs/mapping/release/configuration.txt`, qui liste les
 * règles réellement reçues par R8, puis vérifiée dans `mapping.txt`.
 */
val classesKeptByName = listOf(
  // Instanciées par le système d'après le nom écrit dans AndroidManifest.xml.
  "io.github.mgdx.escale.EscaleApplication",
  "io.github.mgdx.escale.MainActivity",
  // Room appelle `Class.forName("<base>_Impl")`. Protégée par la règle de `room-runtime`
  // (`-keep class * extends androidx.room.RoomDatabase { void <init>(); }`).
  "io.github.mgdx.escale.data.db.EscaleDatabase_Impl",
  // Trouvées depuis le moteur natif de MapLibre. Protégées par `@Keep` et le fichier de règles par
  // défaut d'AGP, pas par le `proguard.txt` de l'AAR — d'où l'intérêt de le vérifier.
  "org.maplibre.android.maps.NativeMapView",
  "org.maplibre.android.maps.renderer.MapRenderer",
  "org.maplibre.android.storage.FileSource",
  "org.maplibre.android.geometry.LatLng",
  "org.maplibre.android.geometry.LatLngBounds",
  "org.maplibre.android.style.layers.Layer",
  "org.maplibre.android.style.sources.Source",
)

val releaseMappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")

val verifyReleaseKeepRules = tasks.register("verifyReleaseKeepRules") {
  group = "verification"
  description = "Vérifie que R8 n'a ni supprimé ni renommé les classes désignées par leur nom."
  val mapping = releaseMappingFile
  // Recopiées dans des variables locales : une lambda `doLast` qui lit une propriété du script
  // capture le script lui-même, que le cache de configuration ne sait pas sérialiser.
  val expected = classesKeptByName
  doLast {
    val lines = mapping.get().asFile.readLines()
    val renamedTo = lines
      .filter { it.isNotEmpty() && !it.startsWith(" ") && it.contains(" -> ") }
      .associate { line ->
        val separator = line.indexOf(" -> ")
        line.substring(0, separator) to line.substring(separator + 4).removeSuffix(":")
      }
    val broken = expected.mapNotNull { name ->
      when (renamedTo[name]) {
        null -> "$name : supprimée par R8"
        name -> null
        else -> "$name : renommée en ${renamedTo[name]}"
      }
    }
    // Les sérialiseurs engendrés par kotlinx.serialization : R8 les marque `R8$$REMOVED$$CLASS$$n`
    // quand il les supprime. Un DTO dont le sérialiseur disparaît rend toute réponse de l'API
    // illisible, et rien d'autre ne le signalerait.
    val removedSerializers = renamedTo
      .filterKeys { it.startsWith("io.github.mgdx.escale") && it.endsWith("\$\$serializer") }
      .filterValues { it.startsWith("R8\$\$REMOVED") }
      .keys
      .map { "$it : sérialiseur supprimé par R8" }
    val failures = broken + removedSerializers
    if (failures.isNotEmpty()) {
      error(
        "Minification cassée : la publication se compilerait sans rien signaler et échouerait sur " +
          "l'appareil. Ajoute la règle manquante dans app/src/main/keepRules/.\n" +
          failures.joinToString("\n") { "  - $it" },
      )
    }
  }
}

val releaseApkDirectory = layout.buildDirectory.dir("outputs/apk/release")

val verifyReleaseApkSize = tasks.register("verifyReleaseApkSize") {
  group = "verification"
  description = "Vérifie le budget de SPEC.md § 2 : moins de 15 Mo par APK d'architecture."
  val apks = releaseApkDirectory
  val budget = apkSizeBudgetBytes
  doLast {
    val files = apks.get().asFile.listFiles { file -> file.name.endsWith(".apk") }.orEmpty().sorted()
    check(files.isNotEmpty()) { "Aucun APK de publication à mesurer dans ${apks.get().asFile}" }
    files.forEach { logger.lifecycle("  ${it.name} : ${it.length() / 1_000_000.0} Mo") }
    val overBudget = files.filter { it.length() > budget }
    if (overBudget.isNotEmpty()) {
      error(
        "Budget de SPEC.md § 2 dépassé (${budget / 1_000_000} Mo par APK d'architecture) :\n" +
          overBudget.joinToString("\n") { "  - ${it.name} : ${it.length() / 1_000_000.0} Mo" },
      )
    }
  }
}

/**
 * Le garde-fou de la convention de `versionCode` et du découpage par ABI.
 *
 * Deux choses se cassent en silence et ne se voient qu'à la publication : un `versionCode` qui
 * cesserait de valoir `rang × 1000 + baseVersionCode`, et une compilation `-Pabi` qui rendrait
 * autre chose que l'unique APK demandé — ce que `fdroid build` refuse. Les deux se lisent dans
 * `output-metadata.json`, qu'AGP écrit à côté des APK ; cette tâche les vérifie sur le fichier
 * produit, pas sur l'intention.
 */
val verifyReleaseVersionCodes = tasks.register("verifyReleaseVersionCodes") {
  group = "verification"
  description = "Vérifie le versionCode par ABI et la liste des APK produits (docs/fdroid.md § 6)."
  val metadata = releaseApkDirectory.map { it.file("output-metadata.json") }
  val ranks = abiVersionCodeRanks
  val multiplier = abiVersionCodeMultiplier
  val base = baseVersionCode
  val expectedAbis = includedAbis.toSet()
  doLast {
    // Projections étoilées plutôt que types génériques : `JsonSlurper` rend des `Any?`, et un
    // transtypage générique ne serait pas vérifiable — donc un avertissement, que CLAUDE.md
    // n'accepte pas.
    val parsed = groovy.json.JsonSlurper().parse(metadata.get().asFile) as Map<*, *>
    val elements = (parsed["elements"] as List<*>).filterIsInstance<Map<*, *>>()
    val produced = elements.associate { element ->
      val filters = (element["filters"] as List<*>).filterIsInstance<Map<*, *>>()
      val abi = filters.single { it["filterType"] == "ABI" }["value"] as String
      abi to (element["versionCode"] as Number).toInt()
    }
    val failures = buildList {
      if (produced.keys != expectedAbis) {
        add(
          "APK produits : ${produced.keys.sorted()} ; attendus : ${expectedAbis.sorted()}. " +
            "Avec -Pabi, un seul APK doit rester dans le répertoire de sortie.",
        )
      }
      produced.forEach { (abi, versionCode) ->
        val expected = (ranks[abi] ?: 0) * multiplier + base
        if (versionCode != expected) {
          add("$abi : versionCode $versionCode, attendu $expected (rang × $multiplier + $base).")
        }
      }
    }
    if (failures.isNotEmpty()) {
      error(
        "Convention de publication rompue — elle est figée une fois l'application publiée " +
          "(app/build.gradle.kts, docs/fdroid.md § 6) :\n" +
          failures.joinToString("\n") { "  - $it" },
      )
    }
    produced.toSortedMap().forEach { (abi, versionCode) ->
      logger.lifecycle("  $abi : versionCode $versionCode")
    }
  }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
  finalizedBy(verifyReleaseKeepRules, verifyReleaseApkSize, verifyReleaseVersionCodes)
}
