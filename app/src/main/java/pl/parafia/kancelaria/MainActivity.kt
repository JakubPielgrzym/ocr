package pl.parafia.kancelaria

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import pl.parafia.kancelaria.ui.App
import pl.parafia.kancelaria.ui.KancelariaTheme

class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()

    /** true, gdy świadomie opuszczamy aplikację (skaner, zapis pliku) - wtedy nie blokujemy. */
    private var externalFlow = false
    private var promptShowing = false
    private var pendingExport: String? = null

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        externalFlow = false
        if (result.resultCode == RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scan?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) vm.onScanResult(uris)
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        externalFlow = false
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Zapisano plik (niezaszyfrowany!)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                vm.message = "Nie udało się zapisać pliku: ${e.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Brak zrzutów ekranu i podglądu w liście ostatnich aplikacji.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            KancelariaTheme {
                App(
                    vm = vm,
                    onScan = ::startScan,
                    onExport = ::export,
                    onUnlock = ::authenticate,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        if (!externalFlow && !isChangingConfigurations) vm.lock()
    }

    // ---------- blokada: biometria lub PIN/wzór/hasło telefonu ----------

    private fun authenticate() {
        if (promptShowing) return
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            vm.authError = "Ustaw w telefonie blokadę ekranu (PIN, wzór lub odcisk palca), aby korzystać z aplikacji."
            return
        }
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    vm.unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    vm.authError = errString.toString()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kancelaria OCR")
            .setSubtitle("Potwierdź tożsamość, aby odblokować dane")
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
    }

    // ---------- skaner dokumentów (na urządzeniu) ----------

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        externalFlow = true
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                externalFlow = false
                vm.message = "Nie można uruchomić skanera: ${e.message}"
            }
    }

    // ---------- eksport do zwykłego pliku .txt ----------

    private fun export(fileName: String, content: String) {
        pendingExport = content
        externalFlow = true
        exportLauncher.launch(fileName)
    }
}
