<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { defaultFrontendSettings } from './frontend-settings';

const props = defineProps<{
  baseUrl: string;
}>();

const emit = defineEmits<{
  'update:baseUrl': [string];
}>();

const loading = ref(false);
const errorMessage = ref('');
const responsePreview = ref('');
const responseStatus = ref('');
const lastFetchedAt = ref('');

const normalizedBaseUrl = computed(() => {
  const candidate = props.baseUrl.trim() || defaultFrontendSettings.backendBaseUrl;
  return candidate.replace(/\/+$/, '');
});

const endpointUrl = computed(() => `${normalizedBaseUrl.value}/api/setting`);

const handleBaseUrlInput = (event: Event) => {
  const target = event.target as HTMLInputElement | null;
  emit('update:baseUrl', target?.value ?? '');
};

const fetchSettings = async () => {
  loading.value = true;
  errorMessage.value = '';

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), 5000);

  try {
    const response = await fetch(endpointUrl.value, {
      headers: {
        Accept: 'application/json, text/plain;q=0.9, */*;q=0.8'
      },
      signal: controller.signal
    });

    const responseText = await response.text();
    let parsedResponse: unknown = responseText;

    if (responseText) {
      try {
        parsedResponse = JSON.parse(responseText);
      } catch {
        parsedResponse = responseText;
      }
    }

    responseStatus.value = `${response.status} ${response.statusText}`.trim();
    responsePreview.value = typeof parsedResponse === 'string'
      ? (parsedResponse || '（空响应）')
      : JSON.stringify(parsedResponse, null, 2);
    lastFetchedAt.value = new Date().toLocaleString();

    if (!response.ok) {
      errorMessage.value = `接口返回异常状态：${responseStatus.value}`;
    }
  } catch (error) {
    responseStatus.value = '请求失败';
    responsePreview.value = '';
    lastFetchedAt.value = new Date().toLocaleString();
    errorMessage.value = error instanceof Error
      ? `无法访问 ${endpointUrl.value}。${error.message}`
      : `无法访问 ${endpointUrl.value}。`;
  } finally {
    window.clearTimeout(timeoutId);
    loading.value = false;
  }
};

onMounted(() => {
  void fetchSettings();
});
</script>

<template>
  <section class="space-y-8">
    <div class="space-y-2 border-b border-[#1A1A1A]/10 pb-6">
      <p class="text-[11px] font-mono uppercase tracking-[0.4em] text-[#E85D04]">后端设置</p>
      <h3 class="text-3xl font-black uppercase tracking-[0.12em] text-[#1A1A1A]">接口联调</h3>
    </div>

    <div class="space-y-6">
      <label class="block space-y-3 rounded-none border-2 border-[#1A1A1A] bg-white/70 p-5">
        <span class="block text-[11px] font-black uppercase tracking-[0.3em] text-[#1A1A1A]">后端地址</span>
        <input
          :value="props.baseUrl"
          type="text"
          class="w-full border border-[#1A1A1A]/20 bg-[#F8F5EC] px-4 py-3 text-sm outline-none transition focus:border-[#E85D04]"
          @input="handleBaseUrlInput"
        />
      </label>

      <div class="rounded-none border-2 border-[#1A1A1A] bg-[#101010] p-5 text-white">
        <div class="flex items-center justify-between gap-4 border-b border-white/10 pb-4">
          <div>
            <p class="text-[10px] font-mono uppercase tracking-[0.35em] text-[#4D908E]">接口地址</p>
            <p class="mt-2 break-all font-mono text-sm text-white/90">{{ endpointUrl }}</p>
          </div>

          <div class="flex items-center gap-3">
            <a
              :href="endpointUrl"
              target="_blank"
              rel="noreferrer"
              class="border border-white/20 px-3 py-2 text-[10px] font-black uppercase tracking-[0.25em] transition hover:border-white hover:bg-white hover:text-[#101010]"
            >
              打开
            </a>
            <button
              type="button"
              @click="fetchSettings"
              class="border border-[#E85D04] bg-[#E85D04] px-3 py-2 text-[10px] font-black uppercase tracking-[0.25em] text-white transition hover:bg-transparent hover:text-[#E85D04]"
            >
              {{ loading ? '加载中' : '刷新' }}
            </button>
          </div>
        </div>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <div class="border border-white/10 p-3">
            <p class="text-[10px] font-mono uppercase tracking-[0.3em] text-white/40">状态</p>
            <p class="mt-2 font-mono text-sm">{{ responseStatus || '等待请求' }}</p>
          </div>
          <div class="border border-white/10 p-3">
            <p class="text-[10px] font-mono uppercase tracking-[0.3em] text-white/40">更新时间</p>
            <p class="mt-2 font-mono text-sm">{{ lastFetchedAt || '未请求' }}</p>
          </div>
        </div>

        <div v-if="errorMessage" class="mt-4 border border-[#E85D04]/50 bg-[#E85D04]/10 p-4 text-sm leading-7 text-[#FFD4BF]">
          {{ errorMessage }}
        </div>

        <pre class="mt-4 max-h-[320px] overflow-auto border border-white/10 bg-black/30 p-4 text-xs leading-6 text-white/80">{{ responsePreview || '// 等待后端响应' }}</pre>
      </div>
    </div>
  </section>
</template>
