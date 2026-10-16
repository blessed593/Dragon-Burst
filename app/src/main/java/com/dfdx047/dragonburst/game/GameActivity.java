package com.dfdx047.dragonburst.game;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Atividade do jogo. O SDLActivity carrega libSDL3.so e libmain.so e chama
 * SDL_main (windowed_app_main_sdl.cpp) numa thread propria.
 *
 * Aberta pelo launcher (LauncherActivity) ou pelo atalho da tela inicial. As
 * opcoes do jogo ja estao no burstlimit.toml (escrito pelo launcher); aqui so se
 * aplica a orientacao, o driver Vulkan e os controles por toque (GameOverlay).
 *
 * Antes disso definimos as variaveis de ambiente que o runtime le no Android:
 *   REX_APP_DIR            pasta de dados (jogo, configuracoes, logs, cache)
 *   REX_NATIVE_LIB_DIR     pasta das .so extraidas (plugin da GPU)
 * (O SDK trata REX_APP_DIR como a pasta do executavel: o jogo fica em
 * game_data_root/ e o burstlimit.toml ao lado, como no PC.)
 */
public class GameActivity extends SDLActivity {
    private static final String TAG = "dbz3";

    private GameOverlay mOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyOrientation();

        File appDir = getExternalFilesDir(null);
        if (appDir == null) {
            appDir = getFilesDir();
        }
        //noinspection ResultOfMethodCallIgnored
        appDir.mkdirs();

        setEnv("REX_APP_DIR", appDir.getAbsolutePath());
        setEnv("REX_NATIVE_LIB_DIR", getApplicationInfo().nativeLibraryDir);
        // Compilacao de pipelines sincrona: no Android a async escondia 1 frame
        // de cada vez que um pipeline faltava (travadinhas), e a sincrona custa
        // menos de 1 ms por frame com a cache do driver. O cvars.txt pode mudar.
        setEnv("REX_ASYNC_SHADER_COMPILATION", "false");
        applyTurnipCompat(appDir);
        applyLanOnline();
        applyCvarOverrides(new File(appDir, "cvars.txt"));
        if (!driverCrashedBefore(appDir)) {
            installCustomVulkanDriver(appDir);
        }
        prepareLogFile(appDir);

        Log.i(TAG, "Pasta do jogo: " + appDir.getAbsolutePath() + "/game_data_root");
        super.onCreate(savedInstanceState);

        enterImmersive();
        requestSixtyHz();
        // Jogo com carga constante: pede ao sistema para nao deixar a GPU/CPU cair em
        // estados de poupanca entre frames (buracos de 10-15 ms na GPU, logs de
        // 2026-10-13). "env:DF_NO_SUSTAINED = 1" no cvars.txt desliga, para A/B.
        if (!"1".equals(Os.getenv("DF_NO_SUSTAINED"))) {
            try {
                getWindow().setSustainedPerformanceMode(true);
                Log.i(TAG, "Modo de desempenho sustentado: ligado");
            } catch (RuntimeException e) {
                Log.w(TAG, "Modo de desempenho sustentado indisponivel", e);
            }
        }

        // Controles por toque por cima do jogo (se nao foram desligados no launcher).
        mOverlay = new GameOverlay(this);
        mOverlay.onCreate();
    }

    /**
     * Ecra inteiro imersivo (sem barra de estado nem de navegacao, por baixo do recorte).
     * Sem isto o SDL aplicava o estilo "janela" (o jogo cria a janela com fullscreen=false)
     * e a superficie ficava mais pequena que o ecra: 1920x918 num Retroid Pocket 5 de
     * 1920x1080 (logs de 2026-10-14), com a imagem cortada/encolhida nos portateis 16:9.
     * O SDK tambem passou a pedir ecra inteiro no Android; isto fica como reforco (e
     * repoe as barras escondidas quando a janela volta a ter foco).
     */
    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams params = window.getAttributes();
        if (params.layoutInDisplayCutoutMode
                != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
        window.setDecorFitsSystemWindows(false);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersive();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mOverlay != null) {
            mOverlay.onResume();
        }
    }

    @Override
    protected void onPause() {
        clearDriverBootPending();
        if (mOverlay != null) {
            mOverlay.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mOverlay != null) {
            mOverlay.onDestroy();
            mOverlay = null;
        }
        super.onDestroy();
    }

    /** Superficie do jogo (autoteste do render: fotos por PixelCopy). */
    public static android.view.SurfaceView gameSurface() {
        return mSurface;
    }

    /** Com o menu dos controles aberto, o Back do Android fecha o menu (nao vai para o jogo). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mOverlay != null) {
            mOverlay.onPhysicalInput(event);
            // Menu do jogo do Android: navegacao pelo comando e Back + Start para o abrir.
            if (mOverlay.onMenuKeyEvent(event)) {
                return true;
            }
        }
        // Back (o botao Back do Odin e de outros portateis, ou o gesto de voltar):
        // com um menu aberto volta um nivel; com o jogo a correr abre o menu.
        if (mOverlay != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (mOverlay.isMenuOpen()) {
                    mOverlay.onBackPressed();
                } else {
                    mOverlay.openMenu();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mOverlay != null) {
            mOverlay.onPhysicalInput(event);
            if (mOverlay.onMenuMotion(event)) {
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /** Controles no modo automatico: o primeiro toque na tela fa-los aparecer. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mOverlay != null && mOverlay.onScreenTouch(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * O jogo gera 60 frames/s por um relogio proprio (o vblank do Xenos), que nao
     * esta preso ao ecra. Num ecra de 120 Hz com FIFO cada frame fica 2 vblanks
     * na tela, mas basta o relogio oscilar 1 ms para alguns ficarem 1 ou 3
     * (8 ms / 25 ms): e o "stutter" que se ve mesmo com o contador nos 60. O
     * preferredRefreshRate sozinho e so uma sugestao que varios fabricantes
     * ignoram, por isso pedimos o modo de 60 Hz pelo id e, alem disso, marcamos
     * a superficie do SDL como fonte fixa de 60 FPS (API 30).
     */
    private void requestSixtyHz() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 60.0f;
        StringBuilder modes = new StringBuilder();
        try {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode current = display.getMode();
            Display.Mode best = null;
            for (Display.Mode m : display.getSupportedModes()) {
                modes.append(String.format(Locale.ROOT, " %d:%dx%d@%.2f", m.getModeId(),
                        m.getPhysicalWidth(), m.getPhysicalHeight(), m.getRefreshRate()));
                if (m.getPhysicalWidth() != current.getPhysicalWidth()
                        || m.getPhysicalHeight() != current.getPhysicalHeight()) {
                    continue;
                }
                if (Math.abs(m.getRefreshRate() - 60.0f) < 1.0f) {
                    best = m;
                }
            }
            if (best != null) {
                params.preferredDisplayModeId = best.getModeId();
            }
            Log.i(TAG, String.format(Locale.ROOT, "Ecra: atual %.2f Hz, modo pedido %s, modos:%s",
                    display.getRefreshRate(), best != null ? String.valueOf(best.getModeId()) : "nenhum de 60 Hz",
                    modes));
        } catch (RuntimeException e) {
            Log.w(TAG, "Ecra: nao foi possivel escolher o modo de 60 Hz", e);
        }
        getWindow().setAttributes(params);

        if (mSurface != null) {
            mSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    try {
                        holder.getSurface().setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Surface.setFrameRate falhou", e);
                    }
                    // O sistema muda o modo com algum atraso: regista o que ficou.
                    mSurface.postDelayed(() -> {
                        Display d = getWindowManager().getDefaultDisplay();
                        Log.i(TAG, String.format(Locale.ROOT, "Ecra durante o jogo: %.2f Hz", d.getRefreshRate()));
                    }, 3000);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });
        }
    }

    /** Orientacao escolhida no launcher: paisagem, paisagem invertida ou automatica. */
    private void applyOrientation() {
        @SuppressWarnings("deprecation")
        SharedPreferences prefs = getSharedPreferences("dragonburst", MODE_MULTI_PROCESS);
        String mode = prefs.getString("game_orientation", "landscape");
        int orientation;
        if ("reverse".equals(mode)) {
            orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        } else if ("auto".equals(mode)) {
            orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        } else {
            orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        setRequestedOrientation(orientation);
    }

    /**
     * O log desta sessao vai para logs/dragonburst_last.log (a aba Texturas do
     * launcher le-o para mostrar o estado dos packs). O cvars.txt pode mudar.
     */
    private static void prepareLogFile(File appDir) {
        File logs = new File(appDir, "logs");
        //noinspection ResultOfMethodCallIgnored
        logs.mkdirs();
        File log = new File(logs, "dragonburst_last.log");
        //noinspection ResultOfMethodCallIgnored
        log.delete();
        // As partes rodadas (.1.log ...) sao de sessoes antigas: so confundiam a
        // exportacao de logs (20 MB de uma sessao com o log detalhado).
        File[] old = logs.listFiles();
        if (old != null) {
            for (File f : old) {
                String n = f.getName();
                if (n.startsWith("dragonburst_last.") && !n.equals("dragonburst_last.log")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        if (Os.getenv("REX_LOG_FILE") == null) {
            setEnv("REX_LOG_FILE", log.getAbsolutePath());
        }
    }

    @Override
    protected String[] getLibraries() {
        // libmain.so depende de librexruntime.so, librexgpu-xenos.so e
        // libc++_shared.so, que o linker do Android carrega sozinho.
        return new String[] {"SDL3", "main"};
    }

    /**
     * Correcoes para glitches do Turnip nos Adreno 6xx, escolhidas no launcher (aba
     * Drivers) como interruptores independentes (bits da pref "turnip_compat_flags").
     * Antes do cvars.txt, que pode sobrepor qualquer uma. TU_DEBUG so tem efeito no
     * Turnip; o driver da Qualcomm ignora-o.
     *   1  memoria do jogo num so storage buffer de 512 MB (patch 0025): o Turnip dos 6xx
     *      diz 128 MB e a memoria fica partida em 4 com um switch em cada acesso dos
     *      shaders; com isto a geometria do RP5 fica certa (2026-10-13 07:58)
     *   2  TU_DEBUG=sysmem (sem GMEM)
     *   4  TU_DEBUG=nolrz,noubwc
     *   8  texturas BC descomprimidas (vulkan_mask_features=textureCompressionBC)
     *   16 sem dynamic rendering e sem stencil export
     *   32 depth em float 32 (vulkan_disable_depth_unorm24)
     *   64 diagnostico: TU_DEBUG=flushall,syncdraw (se corrigir, falta uma barreira)
     */
    static final int TURNIP_SINGLE_BUFFER = 1, TURNIP_SYSMEM = 2, TURNIP_NOLRZ_UBWC = 4,
            TURNIP_BC_DECOMPRESS = 8, TURNIP_SIMPLE_PASSES = 16, TURNIP_DEPTH_F32 = 32,
            TURNIP_FULL_SYNC = 64;

    /**
     * Online em rede local (LauncherPrefs.LanMode): o metodo "Radmin VPN / LAN" do
     * Burst Limit Recompiled, UDP direto (porta 59395). REX_XNET_IP = IPv4 deste
     * aparelho na rede (Wi-Fi); quem entra recebe tambem REX_XNET_SEARCH_IP.
     */
    private void applyLanOnline() {
        @SuppressWarnings("deprecation")
        SharedPreferences prefs = getSharedPreferences("dragonburst", MODE_MULTI_PROCESS);
        String mode = prefs.getString("lan_mode", "off");
        if (!"host".equals(mode) && !"join".equals(mode)) {
            return;
        }
        String own = localIpv4();
        if (own == null) {
            Log.w(TAG, "online LAN: sem IPv4 de rede local (Wi-Fi desligado?)");
            return;
        }
        setEnv("REX_XNET_IP", own);
        Log.i(TAG, "online LAN: " + mode + " REX_XNET_IP=" + own);
        if ("join".equals(mode)) {
            String host = prefs.getString("lan_host_ip", "");
            if (host != null && host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                setEnv("REX_XNET_SEARCH_IP", host);
                Log.i(TAG, "online LAN: REX_XNET_SEARCH_IP=" + host);
            } else {
                Log.w(TAG, "online LAN: IP do anfitriao invalido: " + host);
            }
        }
    }

    /** IPv4 privado deste aparelho (Wi-Fi/Ethernet primeiro), ou null. */
    public static String localIpv4() {
        String fallback = null;
        try {
            for (java.net.NetworkInterface nif : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(nif.getInetAddresses())) {
                    if (!(a instanceof java.net.Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String name = nif.getName();
                    if (name.startsWith("wlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (fallback == null && !name.startsWith("rmnet")) fallback = a.getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "localIpv4", e);
        }
        return fallback;
    }

    /**
     * Protecao contra um driver que faz o jogo fechar logo no arranque (sem isto o utilizador
     * ficava preso num ciclo de crashes ate apagar o driver). Antes de carregar o driver fica
     * "driver_boot_pending" = nome:tamanho; 30 s depois (ou ao sair normalmente) apaga-se. Se ao
     * abrir de novo ainda la estiver para o mesmo driver, este arranque usa o do sistema e o
     * launcher mostra o aviso ("driver_boot_failed"). Trocar ou reinstalar o driver limpa-o.
     */
    private boolean driverCrashedBefore(File appDir) {
        File[] drivers = new File(appDir, "driver").listFiles((dir, name) -> name.endsWith(".so"));
        @SuppressWarnings("deprecation")
        SharedPreferences prefs = getSharedPreferences("dragonburst", MODE_MULTI_PROCESS);
        if (drivers == null || drivers.length == 0) {
            prefs.edit().remove("driver_boot_pending").remove("driver_boot_failed").commit();
            return false;
        }
        String id = drivers[0].getName() + ":" + drivers[0].length();
        if (id.equals(prefs.getString("driver_boot_failed", ""))
                || id.equals(prefs.getString("driver_boot_pending", ""))) {
            prefs.edit().putString("driver_boot_failed", id).remove("driver_boot_pending").commit();
            Log.w(TAG, "Driver " + id + " fechou o jogo no arranque anterior: a usar o do sistema");
            return true;
        }
        prefs.edit().putString("driver_boot_pending", id).commit();
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::clearDriverBootPending, 30000);
        return false;
    }

    private void clearDriverBootPending() {
        @SuppressWarnings("deprecation")
        SharedPreferences prefs = getSharedPreferences("dragonburst", MODE_MULTI_PROCESS);
        if (prefs.contains("driver_boot_pending")) {
            prefs.edit().remove("driver_boot_pending").apply();
        }
    }

    /** TU_DEBUG que a tabela do TurnipSupport aplica sozinho (vazio = nenhum). */
    private String autoTuDebug = "";

    private void applyTurnipCompat(File appDir) {
        @SuppressWarnings("deprecation")
        SharedPreferences prefs = getSharedPreferences("dragonburst", MODE_MULTI_PROCESS);
        int flags = prefs.getInt("turnip_compat_flags", 0);
        // Com driver alternativo: o que a tabela do TurnipSupport manda para este Snapdragon
        // (ex. TU_DEBUG=sysmem no A830 e nos A710/720/722). O buffer unico de 512 MB e o BC
        // descomprimido ficam so como escolha manual: o Turnip diz 128 MB em todas as geracoes e
        // o problema das cores no RP5 era o do passo 4 (patch 0077), nao estes.
        File[] drivers = new File(appDir, "driver").listFiles((dir, name) -> name.endsWith(".so"));
        boolean custom = drivers != null && drivers.length > 0;
        com.dfdx047.dragonburst.drivers.TurnipSupport.Gpu gpu =
                com.dfdx047.dragonburst.drivers.TurnipSupport.detect();
        Log.i(TAG, "GPU: " + gpu + " kgsl=" + com.dfdx047.dragonburst.drivers.TurnipSupport.hasKgsl()
                + " driver=" + (custom ? drivers[0].getName() : "sistema"));
        if (custom) {
            java.util.HashMap<String, String> env = new java.util.HashMap<>();
            com.dfdx047.dragonburst.drivers.TurnipSupport.applyAutoEnv(gpu, env);
            for (java.util.Map.Entry<String, String> e : env.entrySet()) {
                if (!e.getKey().equals("TU_DEBUG")) {
                    setEnv(e.getKey(), e.getValue());
                    Log.i(TAG, "Turnip: " + e.getKey() + "=" + e.getValue() + " (automatico)");
                }
            }
            autoTuDebug = env.containsKey("TU_DEBUG") ? env.get("TU_DEBUG") : "";
        }
        if (flags == 0 && autoTuDebug.isEmpty()) {
            return;
        }
        StringBuilder tu = new StringBuilder();
        if (!autoTuDebug.isEmpty()) tu.append(autoTuDebug).append(',');
        if ((flags & TURNIP_SYSMEM) != 0) tu.append("sysmem,");
        if ((flags & TURNIP_NOLRZ_UBWC) != 0) tu.append("nolrz,noubwc,");
        if ((flags & TURNIP_FULL_SYNC) != 0) tu.append("flushall,syncdraw,");
        if (tu.length() > 0) {
            tu.setLength(tu.length() - 1);
            setEnv("TU_DEBUG", tu.toString());
        }
        if ((flags & TURNIP_SINGLE_BUFFER) != 0) {
            setEnv("REX_DBZ3_VK_MAX_STORAGE_BUFFER_RANGE_MB", "512");
        }
        if ((flags & TURNIP_BC_DECOMPRESS) != 0) {
            setEnv("REX_VULKAN_MASK_FEATURES", "textureCompressionBC");
        }
        if ((flags & TURNIP_SIMPLE_PASSES) != 0) {
            setEnv("REX_VULKAN_DYNAMIC_RENDERING", "false");
            setEnv("REX_DBZ3_VK_DISABLE_EXTENSIONS", "VK_EXT_shader_stencil_export");
        }
        if ((flags & TURNIP_DEPTH_F32) != 0) {
            setEnv("REX_VULKAN_DISABLE_DEPTH_UNORM24", "true");
        }
        Log.i(TAG, "Correcoes do Turnip: flags " + flags + " TU_DEBUG=" + tu);
    }

    /**
     * Le cvars.txt da pasta da app (linhas "nome = valor", # para comentarios) e
     * passa cada uma ao runtime como variavel REX_NOME. Serve para testar opcoes
     * do runtime sem recompilar: editar o ficheiro e reabrir o jogo.
     */
    private static void applyCvarOverrides(File file) {
        if (!file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (name.startsWith("env:")) {
                    // "env:TU_DEBUG = sysmem": variavel crua para o driver (testes A/B
                    // do Turnip, MESA_*, etc.), sem o prefixo REX_.
                    String env = name.substring(4).trim();
                    setEnv(env, value);
                    Log.i(TAG, "env " + env + " = " + value);
                    continue;
                }
                setEnv("REX_" + name.toUpperCase(Locale.ROOT), value);
                Log.i(TAG, "cvar " + name + " = " + value);
            }
        } catch (IOException e) {
            Log.e(TAG, "Falha a ler " + file, e);
        }
    }

    /**
     * Driver Vulkan alternativo (ex. Turnip): se a pasta "driver" da pasta da app
     * tiver um .so, copia-o para a pasta interna (o Android nao deixa carregar
     * bibliotecas a partir do armazenamento partilhado) e diz ao runtime para o
     * usar via libadrenotools. Sem .so nessa pasta, usa-se o driver do sistema.
     */
    private void installCustomVulkanDriver(File appDir) {
        File internalDir = new File(getFilesDir(), "driver");
        File[] candidates = new File(appDir, "driver").listFiles(
                (dir, name) -> name.endsWith(".so"));
        if (candidates == null || candidates.length == 0) {
            deleteRecursively(internalDir);
            return;
        }
        File source = candidates[0];
        File target = new File(internalDir, source.getName());
        try {
            if (!target.isFile() || target.length() != source.length()
                    || target.lastModified() != source.lastModified()) {
                deleteRecursively(internalDir);
                //noinspection ResultOfMethodCallIgnored
                internalDir.mkdirs();
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                //noinspection ResultOfMethodCallIgnored
                target.setLastModified(source.lastModified());
            }
            setEnv("REX_VULKAN_DRIVER_DIR", internalDir.getAbsolutePath() + "/");
            setEnv("REX_VULKAN_DRIVER_NAME", target.getName());
            Log.i(TAG, "Driver Vulkan alternativo: " + target.getName());
        } catch (IOException e) {
            Log.e(TAG, "Falha a instalar o driver " + source, e);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void setEnv(String name, String value) {
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Falha a definir " + name, e);
        }
    }
}
