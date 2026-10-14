// Dragon Burst - estado do jogo para os controles por toque e para o menu do port.
//
// Le a memoria do jogo pelo Runtime (Memory::TranslateVirtual), com os enderecos
// que o Burst Limit Recompiled ja conhece (build US):
//
//   0x841B5130   ponteiro para o objeto da batalha (0 fora da luta;
//                burstlimit_camera.cpp / burstlimit_discord.cpp)
//   0x84239C10   jogadores; lutador = *(jogador + 0x2C) (burstlimit_kicharge.cpp)
//
// Nao escreve nada na memoria do jogo.

#include <jni.h>

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>

#include <rex/audio/audio_system.h>
#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/memory.h>
#include <rex/memory/utils.h>
#include <rex/runtime.h>
#include <rex/ui/overlay/quick_menu.h>

// android_hooks.cpp: pausa do jogo no inicio de cada tick.
extern std::atomic<bool> g_dragonburst_pause;
extern std::mutex g_dragonburst_pause_mu;
extern std::condition_variable g_dragonburst_pause_cv;

namespace {

constexpr uint32_t kBattlePointer = 0x841B5130;

rex::memory::Memory* GuestMemory() {
  auto* runtime = rex::Runtime::instance();
  return runtime ? runtime->memory() : nullptr;
}

uint32_t LoadU32(rex::memory::Memory* memory, uint32_t address) {
  return rex::memory::load_and_swap<uint32_t>(memory->TranslateVirtual<uint8_t*>(address));
}

uint32_t BattleAddress(rex::memory::Memory* memory) {
  const uint32_t battle = LoadU32(memory, kBattlePointer);
  return battle < 0xF0000000u ? battle : 0;
}

std::mutex g_pause_mu;
bool g_paused = false;

}  // namespace

// Lado do P1. No Burst Limit os botoes de ajuda nao dependem do lado (carregar ki e
// o L3 do proprio port do PC), por isso fica "desconhecido": o HelperEngine usa a
// esquerda como "para tras".
extern "C" JNIEXPORT jint JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeP1Side(JNIEnv*, jclass) {
  return 0;
}

// ---------------------------------------------------------------------------
// Pausa do jogo enquanto um menu esta aberto (menus do launcher por cima do jogo
// ou o menu do port, Back+Start). A logica para no ponto de pausa do tick
// (android_hooks.cpp, chamado pelo BurstLimitCameraFrame) e o audio com
// AudioSystem::Pause(). O Kotlin chama isto a cada tick do HelperEngine (60 Hz).
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeSyncPause(JNIEnv*, jclass, jboolean overlay_open,
                                                                  jboolean pause_quick_menu) {
  std::lock_guard<std::mutex> lock(g_pause_mu);
  auto* rt = rex::Runtime::instance();
  if (!rt || !rt->memory()) return JNI_FALSE;
  const bool want = overlay_open || (pause_quick_menu && rex::ui::QuickMenuDialog::IsOpen());
  if (want == g_paused) return g_paused ? JNI_TRUE : JNI_FALSE;

  auto* audio = static_cast<rex::audio::AudioSystem*>(rt->audio_system());
  if (want) {
    g_dragonburst_pause.store(true, std::memory_order_release);
    if (audio) audio->Pause();
  } else {
    if (audio) audio->Resume();
    {
      std::lock_guard<std::mutex> l(g_dragonburst_pause_mu);
      g_dragonburst_pause.store(false, std::memory_order_release);
    }
    g_dragonburst_pause_cv.notify_all();
  }
  g_paused = want;
  return g_paused ? JNI_TRUE : JNI_FALSE;
}

/** Ao sair do jogo: nunca deixar a thread do jogo presa na pausa. */
extern "C" JNIEXPORT void JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeForceResume(JNIEnv*, jclass) {
  {
    std::lock_guard<std::mutex> l(g_dragonburst_pause_mu);
    g_dragonburst_pause.store(false, std::memory_order_release);
  }
  g_dragonburst_pause_cv.notify_all();
}

// ---------------------------------------------------------------------------
// Luta ou menus: os controles por toque trocam de layout. -1 = ainda nao ha jogo.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jint JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeFightFlag(JNIEnv*, jclass) {
  rex::memory::Memory* memory = GuestMemory();
  if (!memory) return -1;
  return BattleAddress(memory) != 0 ? 1 : 0;
}

// Camara livre / modo foto do Burst Limit (free_camera): o layout "mapa" (comando
// completo com os dois analogicos). 1 = ligada, 0 = nao, -1 = sem jogo.
extern "C" JNIEXPORT jint JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeMapFlag(JNIEnv*, jclass) {
  if (!GuestMemory()) return -1;
  return rex::cvar::GetFlagByName("free_camera") == "true" ? 1 : 0;
}

// Diagnostico da troca luta/menus (logs/controles.log com "Diagnostico de desempenho").
extern "C" JNIEXPORT jstring JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeBattleDebug(JNIEnv* env, jclass) {
  rex::memory::Memory* memory = GuestMemory();
  if (!memory) return env->NewStringUTF("sem jogo");
  char text[96];
  snprintf(text, sizeof(text), "battle=%08X free_camera=%s", LoadU32(memory, kBattlePointer),
           rex::cvar::GetFlagByName("free_camera").c_str());
  return env->NewStringUTF(text);
}

// Diagnostico do lado do P1 (DBZ3). Sem uso no Burst Limit por enquanto.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeDumpFight(JNIEnv*, jclass, jstring, jint,
                                                                  jlong) {
  return JNI_FALSE;
}

// ---------------------------------------------------------------------------
// Menu de opcoes do port por toque (InGameMenu.kt): muda cvars ao vivo, como o menu
// do Burst Limit (Back + Start), e grava o burstlimit.toml.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dfdx047_dragonburst_game_NativeGameState_nativeSetCvars(JNIEnv* env, jclass, jobjectArray names,
                                                                 jobjectArray values) {
  if (!rex::Runtime::instance()) return JNI_FALSE;
  const jsize n = env->GetArrayLength(names);
  bool ok = true;
  for (jsize i = 0; i < n; ++i) {
    auto jn = static_cast<jstring>(env->GetObjectArrayElement(names, i));
    auto jv = static_cast<jstring>(env->GetObjectArrayElement(values, i));
    const char* cn = env->GetStringUTFChars(jn, nullptr);
    const char* cv = env->GetStringUTFChars(jv, nullptr);
    ok = rex::cvar::SetFlagByName(cn, cv) && ok;
    env->ReleaseStringUTFChars(jn, cn);
    env->ReleaseStringUTFChars(jv, cv);
    env->DeleteLocalRef(jn);
    env->DeleteLocalRef(jv);
  }
  rex::cvar::SaveConfig(rex::filesystem::GetExecutableFolder() / "burstlimit.toml");
  return ok ? JNI_TRUE : JNI_FALSE;
}
