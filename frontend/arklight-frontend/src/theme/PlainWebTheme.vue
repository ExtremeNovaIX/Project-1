<script setup lang="ts">
import { Cpu, Send, Settings } from 'lucide-vue-next';
import StardustField from './StardustField.vue';
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
  <div class="h-screen overflow-hidden bg-[linear-gradient(180deg,#f8fafc_0%,#eef2f7_100%)] text-[#1f2937]">
    <StardustField :particles="props.stardustParticles" tone="cool" />

    <Transition name="plain-boot-fade">
      <div
        v-if="props.isBooting"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-[rgba(248,250,252,0.96)] backdrop-blur-sm"
      >
        <div class="w-full max-w-md rounded-[28px] border border-white/80 bg-white/95 px-8 py-7 text-center shadow-[0_24px_80px_rgba(15,23,42,0.12)]">
          <div class="mx-auto h-12 w-12 rounded-2xl bg-sky-600/10 text-sky-700 flex items-center justify-center text-sm font-semibold">
            {{ props.workspaceName.slice(0, 1) }}
          </div>
          <p class="mt-4 text-sm font-medium text-slate-500">{{ props.themeText.bootLoadingLabel }}</p>
          <h1 class="mt-2 text-2xl font-semibold text-slate-900">{{ props.workspaceName }}</h1>
        </div>
      </div>
    </Transition>

    <div class="border-b border-slate-200/80 bg-white/80 backdrop-blur">
      <div class="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 lg:px-6">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.28em] text-sky-600">Workspace</p>
          <h1 class="mt-2 text-2xl font-semibold text-slate-900">{{ props.workspaceName }}</h1>
        </div>
        <div class="flex items-center gap-3">
          <div class="hidden rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-sm text-slate-500 md:block">
            操作员：<span class="font-medium text-slate-800">{{ props.operatorName }}</span>
          </div>
          <button
            type="button"
            @click="emit('open-settings')"
            class="inline-flex items-center gap-2 rounded-2xl bg-slate-900 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-700"
          >
            <Settings :size="18" />
            {{ props.themeText.settingsButtonLabel }}
          </button>
        </div>
      </div>
    </div>

    <div class="mx-auto flex min-h-[calc(100vh-89px)] max-w-7xl flex-col gap-6 px-4 py-6 lg:flex-row lg:px-6">
      <aside class="w-full shrink-0 space-y-6 lg:w-[320px]">
        <section class="rounded-[28px] border border-white/80 bg-white p-6 shadow-[0_18px_50px_rgba(15,23,42,0.08)]">
          <div class="overflow-hidden rounded-[24px] border border-slate-200 bg-[linear-gradient(180deg,#f8fafc_0%,#edf2f7_100%)]">
            <div class="flex min-h-[520px] items-end justify-center p-6">
              <img
                v-if="props.characterImageUrl"
                :src="props.characterImageUrl"
                :alt="props.characterName || '角色立绘'"
                class="max-h-[500px] w-full object-contain object-bottom"
              />
              <div v-else class="flex h-[360px] w-full items-center justify-center rounded-[20px] border border-dashed border-slate-300 bg-white text-sm text-slate-400">
                未加载角色
              </div>
            </div>
          </div>
          <div class="mt-4">
            <p class="text-sm font-medium text-slate-500">当前角色</p>
            <p class="mt-1 text-xl font-semibold text-slate-900">{{ props.characterName || '未选择' }}</p>
            <p class="mt-1 text-sm text-slate-500">{{ props.activeCharacterEmotion || '默认表情' }}</p>
          </div>
        </section>

        <section class="rounded-[28px] border border-white/80 bg-white p-6 shadow-[0_18px_50px_rgba(15,23,42,0.08)]">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.26em] text-slate-400">概览</p>
            <h2 class="mt-3 text-2xl font-semibold text-slate-900">工作台</h2>
          </div>

          <div class="mt-8 grid gap-4">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <p class="text-sm text-slate-500">操作员</p>
              <p class="mt-1 text-base font-medium text-slate-900">{{ props.operatorName }}</p>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <p class="text-sm text-slate-500">后端地址</p>
              <p class="mt-1 break-all text-sm text-slate-900">{{ props.backendBaseUrl }}</p>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <p class="text-sm text-slate-500">消息数</p>
                <p class="mt-1 text-xl font-semibold text-slate-900">{{ props.messages.length }}</p>
              </div>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <p class="text-sm text-slate-500">状态</p>
                <p class="mt-1 text-xl font-semibold text-emerald-600">在线</p>
              </div>
            </div>
          </div>
        </section>

      </aside>

      <main class="flex min-h-0 min-w-0 flex-1 flex-col gap-6">
        <header class="rounded-[28px] border border-white/80 bg-white p-6 shadow-[0_18px_50px_rgba(15,23,42,0.08)]">
          <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <h1 class="text-3xl font-semibold text-slate-900">聊天面板</h1>
            </div>
            <div class="grid grid-cols-1 gap-3 md:w-[150px]">
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <p class="text-xs text-slate-500">输入状态</p>
                <p class="mt-1 text-sm font-medium text-slate-900">{{ props.userInput ? '编辑中' : '空闲' }}</p>
              </div>
            </div>
          </div>
        </header>

        <section class="flex min-h-0 min-w-0 flex-1 flex-col rounded-[28px] border border-white/80 bg-white shadow-[0_18px_50px_rgba(15,23,42,0.08)]">
          <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
            <h2 class="text-lg font-semibold text-slate-900">消息记录</h2>
            <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500">
              {{ props.messages.length }} 条
            </span>
          </div>

          <div
            data-chat-scroller="main"
            class="flex-1 min-w-0 space-y-4 overflow-x-hidden overflow-y-auto bg-[linear-gradient(180deg,#ffffff_0%,#f8fafc_100%)] px-6 py-5"
          >
            <article
              v-for="message in props.messages"
              :key="message.id"
              :class="[
                'w-fit min-w-0 max-w-[min(86%,32rem)] rounded-[22px] border px-4 py-3 shadow-sm sm:px-5 sm:py-4',
                message.role === 'user'
                  ? 'ml-auto border-sky-200 bg-sky-50/90'
                  : 'border-slate-200 bg-white'
              ]"
            >
              <p class="text-xs font-medium uppercase tracking-[0.2em]" :class="message.role === 'user' ? 'text-sky-700' : 'text-slate-500'">
                {{ message.role === 'user' ? `${props.themeText.userLabelPrefix} / ${props.operatorName}` : props.themeText.assistantLabel }}
              </p>
              <p class="mt-3 whitespace-pre-wrap break-all text-sm leading-7 text-slate-800 [overflow-wrap:anywhere]">{{ message.content }}</p>
            </article>

            <article
              v-if="props.isAssistantTyping"
              class="w-fit min-w-0 max-w-[min(86%,32rem)] rounded-[22px] border border-slate-200 bg-white px-4 py-3 shadow-sm sm:px-5 sm:py-4"
            >
              <p class="text-xs font-medium uppercase tracking-[0.2em] text-slate-500">
                {{ props.themeText.assistantLabel }}
              </p>
              <div class="mt-3 flex items-center gap-2">
                <span class="h-2.5 w-2.5 animate-pulse rounded-full bg-sky-500"></span>
                <span class="h-2.5 w-2.5 animate-pulse rounded-full bg-sky-500 [animation-delay:120ms]"></span>
                <span class="h-2.5 w-2.5 animate-pulse rounded-full bg-sky-500 [animation-delay:240ms]"></span>
                <span class="ml-3 text-sm text-slate-500">{{ props.themeText.typingLabel }}</span>
              </div>
            </article>
          </div>
        </section>

        <section class="min-w-0 rounded-[28px] border border-white/80 bg-white p-6 shadow-[0_18px_50px_rgba(15,23,42,0.08)]">
          <div class="flex flex-col gap-4">
            <div class="relative min-w-0 overflow-hidden">
              <textarea
                :value="props.userInput"
                :placeholder="props.themeText.inputPlaceholder"
                class="min-h-[140px] w-full min-w-0 resize-none overflow-x-hidden rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4 pr-12 text-sm leading-7 outline-none transition [overflow-wrap:anywhere] [word-break:break-all] focus:border-sky-400 focus:bg-white"
                @input="handleUserInput"
                @keydown.enter.prevent="handleEnterPress"
              ></textarea>
              <div class="absolute right-4 top-4 text-slate-400">
                <Cpu :size="18" />
              </div>
            </div>

            <div class="flex justify-end">
              <button
                type="button"
                :disabled="props.isSendDisabled || !props.userInput.trim()"
                @click="emit('send-message')"
                class="inline-flex items-center gap-2 rounded-2xl bg-sky-600 px-5 py-3 text-sm font-medium text-white transition hover:bg-sky-500 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:hover:bg-slate-300"
              >
                <Send :size="18" />
                {{ props.themeText.sendButtonLabel }}
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.plain-boot-fade-leave-active {
  transition: opacity 0.3s ease;
}

.plain-boot-fade-leave-to {
  opacity: 0;
}
</style>
