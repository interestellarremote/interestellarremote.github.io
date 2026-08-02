package com.antigravity.remote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.antigravity.remote.billing.BillingManager
import com.antigravity.remote.data.PairingPayload
import com.antigravity.remote.ui.RemoteApp
import com.antigravity.remote.ui.RemoteViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val LAUNCH_SPLASH_HOLD_MS = 550L
        private const val LAUNCH_SPLASH_FADE_MS = 400
    }

    private val viewModel: RemoteViewModel by viewModels()
    private val billingManager by lazy { BillingManager(this) }
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
            GoogleAuthProvider.getCredential(account.idToken, null)
        }.onSuccess { credential ->
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { viewModel.onSignedIn() }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handlePairingUri(intent?.data)
        setContent {
            val billingState by billingManager.state.collectAsStateWithLifecycle()
            var showLaunchSplash by remember { mutableStateOf(true) }
            var fadeLaunchSplash by remember { mutableStateOf(false) }
            val splashAlpha by animateFloatAsState(
                targetValue = if (fadeLaunchSplash) 0f else 1f,
                animationSpec = tween(LAUNCH_SPLASH_FADE_MS),
                label = "launch splash fade",
            )
            LaunchedEffect(Unit) {
                delay(LAUNCH_SPLASH_HOLD_MS)
                fadeLaunchSplash = true
                delay(LAUNCH_SPLASH_FADE_MS.toLong())
                showLaunchSplash = false
            }
            Box(Modifier.fillMaxSize()) {
                RemoteApp(
                    viewModel = viewModel,
                    billingState = billingState,
                    onPurchase = { productId ->
                        billingManager.launchPurchase(
                            activity = this@MainActivity,
                            productId = productId,
                            accountId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
                        )
                    },
                    onManageSubscription = { billingManager.openSubscriptionManagement(this@MainActivity) },
                    onOpenPcDownload = ::openPcDownloadPage,
                    onLogin = ::login,
                    onLogout = ::logout,
                    onScan = ::startQrScanner,
                    onPairingUri = ::handlePairingUri,
                )
                if (showLaunchSplash) LaunchSplashScreen(splashAlpha)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairingUri(intent.data)
    }

    @Deprecated("ZXing's embedded scanner returns its result through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val scanResult: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scanResult != null) {
            scanResult.contents?.let { handlePairingUri(Uri.parse(it)) }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        billingManager.connect()
        viewModel.onAppForegrounded()
    }

    override fun onDestroy() {
        billingManager.close()
        super.onDestroy()
    }

    private fun login() {
        signInLauncher.launch(googleSignInClient().signInIntent)
    }

    private fun openPcDownloadPage() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DownloadLinks.OFFICIAL_SITE_URL)))
    }

    private fun startQrScanner() {
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Aponte a câmera para o QR Code do Bridge")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan()
    }

    private fun logout() {
        viewModel.signOut()
        googleSignInClient().signOut()
    }

    private fun googleSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(resources.getIdentifier("default_web_client_id", "string", packageName)))
            .requestEmail().build()
        return GoogleSignIn.getClient(this, options)
    }

    private fun handlePairingUri(uri: Uri?) {
        if (uri?.scheme != "agyremote" || uri.host != "pair") return
        runCatching {
            val encoded = requireNotNull(uri.getQueryParameter("payload"))
            val json = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            PairingPayload.fromJson(JSONObject(json))
        }.onSuccess(viewModel::pair)
    }
}

@androidx.compose.runtime.Composable
private fun LaunchSplashScreen(alpha: Float) {
    Box(Modifier.fillMaxSize().alpha(alpha).background(Color.Black)) {
        Image(
            painter = painterResource(com.antigravity.remote.R.drawable.launch_background),
            contentDescription = "Interestellar Remote",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
