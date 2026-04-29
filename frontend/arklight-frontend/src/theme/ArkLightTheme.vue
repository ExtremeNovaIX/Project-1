<script setup lang="ts">
import { ChevronRight, Cpu, Orbit, Send, Settings, User } from 'lucide-vue-next';
import type { Message, StardustParticle, ThemeText } from './types';

const props = defineProps<{
  workspaceName: string;
  operatorName: string;
  backendBaseUrl: string;
  characterName: string;
  characterImageUrl: string;
  activeCharacterEmotion: string;
  isAssistantTyping: boolean;
  isSendDisabled: boolean;
  messages: Message[];
  userInput: string;
  isBooting: boolean;
  showTitle: boolean;
  stardustParticles: StardustParticle[];
  themeText: ThemeText;
}>();

const emit = defineEmits<{
  'open-settings': [];
  'update:userInput': [string];
  'send-message': [];
}>();

const handleUserInput = (event: Event) => {
  const target = event.target as HTMLTextAreaElement | null;
  emit('update:userInput', target?.value ?? '');
};

const handleEnterPress = () => {
  if (props.isSendDisabled || !props.userInput.trim()) {
    return;
  }

  emit('send-message');
};

</script>

<template>
  <div class="h-screen w-screen overflow-hidden bg-[#F6F0DC] font-sans text-[#1A1A1A] select-none relative">
    <StardustField :particles="props.stardustParticles" tone="warm" />

    <Transition name="arklight-boot-fade">
      <div
        v-if="props.isBooting"
        class="absolute inset-0 z-[100] flex flex-col items-center justify-center overflow-hidden bg-[#0A0A0A]"
      >
        <div class="absolute inset-0 opacity-20 pointer-events-none">
          <svg width="100%" height="100%" viewBox="0 0 1000 1000" preserveAspectRatio="xMidYMid slice" class="arklight-pulse-slow">
            <defs>
              <pattern id="arklight-grid" width="100" height="100" patternUnits="userSpaceOnUse">
                <path d="M 100 0 L 0 0 0 100" fill="none" stroke="#4D908E" stroke-width="0.5" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#arklight-grid)" />
            <g stroke="#4D908E" stroke-width="1" fill="none" class="arklight-float">
              <circle cx="500" cy="500" r="200" stroke-dasharray="10 5" />
              <circle cx="500" cy="500" r="150" />
              <path d="M 300 500 L 700 500 M 500 300 L 500 700" stroke-dasharray="5 5" />
              <rect x="400" y="400" width="200" height="200" stroke-dasharray="2 2" />
              <path d="M 400 400 L 600 600 M 600 400 L 400 600" />
            </g>
          </svg>
        </div>

        <div class="relative z-10 flex flex-col items-center">
          <Transition name="arklight-slide-up">
            <div v-if="props.showTitle" class="flex flex-col items-center gap-4">
              <h1 class="arklight-glitch text-6xl font-black uppercase italic tracking-[0.5em] text-white">
                ARKLIGHT
              </h1>
              <div class="arklight-expand-width h-[2px] w-0 bg-[#E85D04]"></div>
              <p class="font-mono text-[10px] uppercase tracking-[1em] text-[#4D908E] opacity-60">
                {{ props.workspaceName }} / Orbital Terminal
              </p>
            </div>
          </Transition>
        </div>

        <div class="absolute inset-0 overflow-hidden pointer-events-none">
          <div class="arklight-scan-fast h-[1px] w-full bg-[#4D908E] opacity-50 shadow-[0_0_20px_#4D908E]"></div>
        </div>
      </div>
    </Transition>

    <div class="relative grid h-full w-full min-w-0 overflow-hidden [grid-template-columns:minmax(320px,40%)_minmax(0,1fr)]">
      <div class="arklight-global-grid absolute inset-0 z-0 pointer-events-none"></div>

      <aside class="relative z-10 flex h-full min-w-0 flex-col overflow-hidden border-r-2 border-[#1A1A1A]/10 bg-[#F1E9D4]">
        <div class="relative z-20 flex h-16 items-center justify-between border-b border-[#1A1A1A]/10 bg-white/26 px-8 backdrop-blur-md">
          <div class="flex items-center gap-4">
            <div class="flex gap-1">
              <div v-for="i in 4" :key="i" class="h-2 w-2 bg-[#E85D04]"></div>
            </div>
            <span class="text-[12px] font-black uppercase tracking-[0.4em] opacity-80">{{ props.workspaceName }}</span>
          </div>
          <div class="flex gap-2">
            <div v-for="i in 2" :key="i" class="h-1 w-8 bg-[#1A1A1A]"></div>
          </div>
        </div>

        <div class="relative z-20 flex-grow overflow-hidden">
          <div class="absolute inset-0 flex items-center justify-center opacity-10 pointer-events-none">
            <div class="grid grid-cols-20 gap-4">
              <div v-for="i in 200" :key="i" class="h-0.5 w-0.5 rounded-full bg-[#1A1A1A]"></div>
            </div>
          </div>

          <div class="group relative h-full w-full">
            <div class="absolute left-4 top-4 z-30 h-16 w-16 border-l-4 border-t-4 border-[#E85D04] transition-all duration-500 group-hover:translate-x-1 group-hover:translate-y-1"></div>
            <div class="absolute bottom-4 right-4 z-30 h-16 w-16 border-b-4 border-r-4 border-[#4D908E] transition-all duration-500 group-hover:-translate-x-1 group-hover:-translate-y-1"></div>

            <div class="absolute inset-0 flex flex-col overflow-hidden border-b-2 border-[#1A1A1A]/10 bg-white/5 shadow-2xl backdrop-blur-md">
              <div
                class="absolute inset-0 opacity-[0.03] pointer-events-none"
                style="background-image: radial-gradient(#1A1A1A 1px, transparent 1px); background-size: 30px 30px;"
              ></div>

              <div class="relative flex h-full w-full items-center justify-center">
                <div class="absolute left-12 top-12 flex flex-col gap-4">
                  <div class="flex h-6 w-24 items-center bg-[#E85D04] px-2">
                    <div class="h-[1px] w-full bg-white/30"></div>
                  </div>
                  <div class="h-6 w-12 bg-[#1A1A1A]"></div>
                </div>

                <div class="relative flex h-full w-full items-center justify-center">
                  <div class="absolute bottom-[11%] z-10 h-[420px] w-[420px] rotate-45 border-4 border-[#1A1A1A] bg-white/10 shadow-[0_0_80px_rgba(0,0,0,0.15)] transition-all duration-1000 group-hover:rotate-0"></div>
                  <div class="arklight-spin-slow absolute h-[450px] w-[450px] rounded-full border border-dashed border-[#1A1A1A]/20"></div>
                  <div class="arklight-spin-reverse absolute h-[550px] w-[550px] rounded-full border border-[#1A1A1A]/5"></div>
                </div>

                <div class="absolute bottom-12 right-12 grid grid-cols-4 gap-2">
                  <div v-for="i in 12" :key="i" class="h-4 w-4 border border-[#4D908E]/20 bg-[#4D908E]/10"></div>
                </div>

              </div>
            </div>

            <div class="pointer-events-none absolute inset-x-0 bottom-0 z-40 flex h-full items-end justify-center overflow-hidden">
              <img
                v-if="props.characterImageUrl"
                :src="props.characterImageUrl"
                :alt="props.characterName || '角色立绘'"
                class="max-h-[96%] w-[90%] object-contain object-bottom drop-shadow-[0_24px_60px_rgba(26,26,26,0.24)]"
              />
              <div
                v-else
                class="mb-10 flex h-[72%] w-[78%] items-center justify-center border border-dashed border-[#1A1A1A]/20 bg-white/20"
              >
                <User :size="220" class="text-[#1A1A1A]/35" />
              </div>
            </div>
          </div>

          <div class="absolute top-1/2 -left-4 flex -translate-y-1/2 flex-col gap-4">
            <div v-for="i in 5" :key="i" class="h-2 w-2 bg-[#1A1A1A]/10"></div>
          </div>
        </div>

        <div class="group relative flex h-24 cursor-pointer items-center justify-between overflow-hidden border-t border-[#1A1A1A]/10 bg-[#1A1A1A] p-8 text-white">
          <div class="absolute inset-0 translate-y-full bg-[#E85D04] transition-transform duration-500 group-hover:translate-y-0"></div>
          <div class="relative z-10 flex items-center gap-6">
            <div class="flex h-12 w-12 items-center justify-center border border-white/20 bg-white/10">
              <Orbit :size="24" class="transition-transform duration-700 group-hover:rotate-180" />
            </div>
            <div class="flex flex-col">
              <span class="font-mono text-[10px] uppercase tracking-widest opacity-50">Orbital Link</span>
              <span class="text-sm font-black uppercase tracking-widest">Established</span>
            </div>
          </div>
          <ChevronRight :size="24" class="relative z-10 opacity-30 transition-transform group-hover:translate-x-2" />
        </div>
      </aside>

      <main class="relative z-10 flex min-w-0 flex-col overflow-hidden bg-[#F6F0DC]">
        <header class="relative flex h-20 shrink-0 items-center justify-end overflow-hidden border-b-2 border-[#1A1A1A] bg-white/52 px-10 backdrop-blur-md">
          <div
            class="absolute inset-0 opacity-[0.02] pointer-events-none"
            style="background-image: radial-gradient(#1A1A1A 2px, transparent 2px); background-size: 10px 10px;"
          ></div>

          <div class="relative z-10 flex items-center gap-6">
            <button
              type="button"
              @click="emit('open-settings')"
              class="flex h-10 items-center justify-center gap-3 border-2 border-[#1A1A1A] px-4 transition-all duration-300 hover:bg-[#1A1A1A] hover:text-white"
            >
              <Settings :size="20" />
              <span class="text-[10px] font-black uppercase tracking-[0.3em]">{{ props.themeText.settingsButtonLabel }}</span>
            </button>
          </div>
        </header>

        <section
          id="arklight-chat-scroller"
          data-chat-scroller="main"
          class="arklight-chat-surface relative min-h-0 flex-grow space-y-5 overflow-x-hidden overflow-y-auto p-6 arklight-scrollbar-hide md:p-8"
        >
          <div class="arklight-chat-pattern pointer-events-none absolute inset-0">
            <div class="arklight-chat-grid absolute inset-0"></div>
          </div>

          <TransitionGroup name="arklight-message-list">
            <div
              v-for="(msg, index) in props.messages"
              :key="msg.id"
              :class="['relative z-10 flex w-full', msg.role === 'user' ? 'justify-end' : 'justify-start']"
            >
              <div
                :class="[
                  'relative flex w-fit min-w-0 max-w-[min(86%,32rem)] flex-col',
                  msg.role === 'user' ? 'items-end' : 'items-start'
                ]"
              >
                <div class="mb-1 flex items-center gap-2 px-2">
                  <div class="h-2 w-2 rounded-full" :class="msg.role === 'user' ? 'bg-[#E85D04]' : 'bg-[#4D908E]'"></div>
                  <span class="font-mono text-[10px] uppercase tracking-[0.28em] opacity-45">
                    {{ msg.role === 'user' ? `${props.themeText.userLabelPrefix} / ${props.operatorName}` : props.themeText.assistantLabel }}
                  </span>
                  <div class="h-[1px] w-8 bg-[#1A1A1A]/10"></div>
                </div>

                <div
                  :class="[
                    'arklight-bubble relative w-fit max-w-full rounded-[20px] px-4 py-3 shadow-[0_10px_32px_rgba(26,26,26,0.08)] sm:px-5 sm:py-4',
                    msg.role === 'user'
                      ? 'arklight-bubble-user bg-[#1A1A1A] text-white'
                      : 'arklight-bubble-ai border border-[#1A1A1A]/8 bg-white'
                  ]"
                >
                  <p class="whitespace-pre-wrap break-all text-[15px] leading-7 [overflow-wrap:anywhere] sm:text-[16px]">
                    {{ msg.content }}
                  </p>
                </div>
              </div>
            </div>
          </TransitionGroup>

          <div v-if="props.isAssistantTyping" class="relative z-10 flex w-full justify-start">
            <div class="flex w-fit min-w-0 max-w-[min(86%,32rem)] flex-col items-start">
              <div class="mb-1 flex items-center gap-2 px-2">
                <div class="h-2 w-2 rounded-full bg-[#4D908E]"></div>
                <span class="font-mono text-[10px] uppercase tracking-[0.28em] opacity-45">
                  {{ props.themeText.assistantLabel }}
                </span>
                <div class="h-[1px] w-8 bg-[#1A1A1A]/10"></div>
              </div>

              <div class="arklight-bubble arklight-bubble-ai relative w-fit max-w-full rounded-[20px] border border-[#1A1A1A]/8 bg-white px-4 py-3 shadow-[0_10px_32px_rgba(26,26,26,0.08)] sm:px-5 sm:py-4">
                <div class="flex items-center gap-2">
                  <span class="h-2.5 w-2.5 animate-pulse rounded-full bg-[#4D908E]"></span>
                  <span class="h-2.5 w-2.5 animate-pulse rounded-full bg-[#4D908E] [animation-delay:120ms]"></span>
                  <span class="h-2.5 w-2.5 animate-pulse rounded-full bg-[#4D908E] [animation-delay:240ms]"></span>
                  <span class="ml-3 text-sm text-[#1A1A1A]/60">{{ props.themeText.typingLabel }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <footer class="shrink-0 overflow-hidden border-t border-[#1A1A1A]/10 bg-white/26 px-6 pt-3 pb-1 backdrop-blur-lg md:px-8 md:pt-4 md:pb-1">
          <div class="relative mx-auto flex max-w-[1060px] min-w-0 items-stretch gap-4 overflow-hidden">
            <div class="group relative min-w-0 flex-grow overflow-hidden">
              <textarea
                :value="props.userInput"
                :placeholder="props.themeText.inputPlaceholder"
                class="min-h-[96px] w-full min-w-0 resize-none overflow-x-hidden rounded-[22px] border border-[#1A1A1A]/15 bg-white px-5 py-3.5 pr-11 text-[15px] leading-7 shadow-inner transition-all [overflow-wrap:anywhere] [word-break:break-all] focus:outline-none focus:ring-4 focus:ring-[#E85D04]/8 sm:text-[16px]"
                @input="handleUserInput"
                @keydown.enter.prevent="handleEnterPress"
              ></textarea>
              <div class="absolute right-0 top-0 p-3 opacity-20 transition-opacity group-hover:opacity-100">
                <Cpu :size="20" class="animate-pulse" />
              </div>
            </div>

            <button
              type="button"
              :disabled="props.isSendDisabled || !props.userInput.trim()"
              @click="emit('send-message')"
              class="group flex min-h-[96px] w-[156px] shrink-0 items-center justify-center gap-2.5 rounded-[22px] bg-[#1A1A1A] text-white shadow-2xl transition-all hover:bg-[#E85D04] active:scale-90 disabled:cursor-not-allowed disabled:bg-[#1A1A1A]/35 disabled:text-white/55 disabled:shadow-none disabled:hover:bg-[#1A1A1A]/35 disabled:active:scale-100"
            >
              <Send :size="28" class="transition-transform group-hover:-translate-y-1 group-hover:translate-x-1" />
              <span class="text-[11px] font-black uppercase tracking-[0.24em]">{{ props.themeText.sendButtonLabel }}</span>
            </button>
          </div>
        </footer>

        <div
          class="arklight-status-strip relative h-[8px] shrink-0 overflow-hidden"
          :title="`${props.workspaceName} | Link: Established | ${props.backendBaseUrl}`"
          :aria-label="`${props.workspaceName} | Link: Established | ${props.backendBaseUrl}`"
        >
          <div class="arklight-status-strip__segments absolute inset-0 flex h-full">
            <span class="arklight-status-strip__segment arklight-status-strip__segment--left flex-1"></span>
            <span class="arklight-status-strip__segment arklight-status-strip__segment--center flex-1"></span>
            <span class="arklight-status-strip__segment arklight-status-strip__segment--right flex-1"></span>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;700;900&family=JetBrains+Mono:wght@400;700&display=swap');

.font-sans {
  font-family: 'Inter', sans-serif;
}

.font-mono {
  font-family: 'JetBrains Mono', monospace;
}

.arklight-spin-slow {
  animation: arklight-spin-slow 30s linear infinite;
}

.arklight-spin-reverse {
  animation: arklight-spin-reverse 25s linear infinite;
}

.arklight-topo-line {
  stroke-dasharray: 10 5;
  animation: arklight-topo-pulse 10s linear infinite;
}

.arklight-scan-fast {
  animation: arklight-scan-fast 2s linear infinite;
}

.arklight-float {
  animation: arklight-float 10s ease-in-out infinite;
}

.arklight-glitch {
  animation: arklight-glitch 0.2s linear infinite;
  animation-play-state: paused;
}

.arklight-expand-width {
  animation: arklight-expand-width 2s ease-out forwards;
}

.arklight-pulse-slow {
  animation: pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.arklight-global-grid {
  opacity: 0.058;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='80' height='80' viewBox='0 0 80 80' fill='none'%3E%3Cpath d='M0 0.5H80M0.5 0V80M0.5 0.5L79.5 79.5M79.5 0.5L0.5 79.5' stroke='%231A1A1A' stroke-opacity='0.085' stroke-width='1' stroke-linecap='square' shape-rendering='geometricPrecision'/%3E%3C/svg%3E");
  background-size: 80px 80px;
  background-position: 0 0;
  background-repeat: repeat;
}

.arklight-scrollbar-hide::-webkit-scrollbar {
  display: none;
}

.arklight-scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

#arklight-chat-scroller::-webkit-scrollbar {
  width: 5px;
}

#arklight-chat-scroller::-webkit-scrollbar-track {
  background: transparent;
}

#arklight-chat-scroller::-webkit-scrollbar-thumb {
  background: #1A1A1A10;
  border-radius: 10px;
}

#arklight-chat-scroller::-webkit-scrollbar-thumb:hover {
  background: #1A1A1A30;
}

.arklight-boot-fade-leave-active {
  transition: opacity 1.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.arklight-boot-fade-leave-to {
  opacity: 0;
}

.arklight-slide-up-enter-active {
  transition: all 1s cubic-bezier(0.16, 1, 0.3, 1);
}

.arklight-slide-up-enter-from {
  opacity: 0;
  transform: translateY(40px);
}

.arklight-message-list-enter-active {
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.arklight-message-list-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.98);
}

.arklight-chat-surface::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(246, 240, 220, 0.84), rgba(246, 240, 220, 0.95));
  pointer-events: none;
  z-index: 0;
}

.arklight-chat-pattern {
  z-index: 0;
  overflow: hidden;
}

.arklight-chat-grid {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='56' height='56' viewBox='0 0 56 56' fill='none'%3E%3Cpath d='M0 0.5H56M0.5 0V56M0.5 0.5L55.5 55.5M55.5 0.5L0.5 55.5' stroke='%23786848' stroke-opacity='0.11' stroke-width='1' stroke-linecap='square' shape-rendering='geometricPrecision'/%3E%3C/svg%3E");
  background-size: 56px 56px;
  background-position: 0 0;
  background-repeat: repeat;
  opacity: 0.42;
}

.arklight-bubble {
  transform: translateY(0) scale(1);
  transition:
    transform 180ms ease,
    box-shadow 220ms ease,
    border-color 220ms ease,
    background-color 220ms ease;
  will-change: transform;
}

.arklight-bubble:hover {
  transform: translateY(-3px) scale(1.01);
}

.arklight-bubble-ai:hover {
  border-color: rgba(77, 144, 142, 0.28);
  box-shadow: 0 16px 34px rgba(26, 26, 26, 0.11);
}

.arklight-bubble-user:hover {
  box-shadow: 0 16px 34px rgba(26, 26, 26, 0.16);
}

.arklight-status-strip__segment--left {
  background: linear-gradient(90deg, #8F2A1F 0%, #A63B28 100%);
}

.arklight-status-strip__segment--center {
  background: linear-gradient(90deg, #DEA03B 0%, #F0B649 100%);
}

.arklight-status-strip__segment--right {
  background: linear-gradient(90deg, #4C8E8B 0%, #67B6B0 100%);
}

@keyframes arklight-spin-slow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes arklight-spin-reverse {
  from { transform: rotate(360deg); }
  to { transform: rotate(0deg); }
}

@keyframes arklight-topo-pulse {
  0% { stroke-dashoffset: 0; opacity: 0.2; }
  50% { opacity: 0.8; }
  100% { stroke-dashoffset: 100; opacity: 0.2; }
}

@keyframes arklight-scan-fast {
  0% { top: -10%; }
  100% { top: 110%; }
}

@keyframes arklight-float {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  50% { transform: translate(10px, -10px) rotate(1deg); }
}

@keyframes arklight-glitch {
  0% { transform: translate(0); }
  20% { transform: translate(-2px, 2px); }
  40% { transform: translate(-2px, -2px); }
  60% { transform: translate(2px, 2px); }
  80% { transform: translate(2px, -2px); }
  100% { transform: translate(0); }
}

@keyframes arklight-expand-width {
  from { width: 0; }
  to { width: 300px; }
}

</style>
