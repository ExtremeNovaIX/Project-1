<script setup lang="ts">
import type { StardustParticle } from './types';

const props = defineProps<{
  particles: StardustParticle[];
  tone: 'warm' | 'cool';
}>();

const buildParticleStyle = (particle: StardustParticle) => ({
  top: `${particle.top}%`,
  left: `${particle.left}%`,
  width: `${particle.size}px`,
  height: `${particle.size}px`,
  animationDuration: `${particle.duration}s`,
  animationDelay: `${particle.delay}s`,
  '--dot-opacity': `${particle.opacity}`,
  '--dot-glow': `${particle.glow}px`,
  '--dot-x': `${particle.driftX}px`,
  '--dot-y': `${particle.driftY}px`
});
</script>

<template>
  <div class="stardust-field">
    <span
      v-for="particle in props.particles"
      :key="particle.id"
      :class="['stardust-dot', props.tone === 'warm' ? 'stardust-dot--warm' : 'stardust-dot--cool']"
      :style="buildParticleStyle(particle)"
    ></span>
  </div>
</template>

<style scoped>
.stardust-field {
  position: fixed;
  inset: 0;
  z-index: 70;
  overflow: hidden;
  pointer-events: none;
  opacity: 1;
}

.stardust-dot {
  position: absolute;
  display: block;
  border-radius: 999px;
  animation: stardust-float ease-in-out infinite;
  will-change: transform, opacity;
}

.stardust-dot::before,
.stardust-dot::after {
  content: '';
  position: absolute;
  display: block;
  inset: 50% auto auto 50%;
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.stardust-dot::before {
  width: 100%;
  height: 100%;
  background: var(--dot-core);
  box-shadow:
    0 0 0 1.35px var(--dot-edge),
    0 0 12px var(--dot-glow-core),
    0 0 var(--dot-glow, 16px) var(--dot-glow-soft);
}

.stardust-dot::after {
  width: calc(100% + 16px);
  height: calc(100% + 16px);
  background: radial-gradient(circle, var(--dot-aura) 0%, rgba(255, 255, 255, 0) 72%);
  opacity: 0.96;
}

.stardust-dot--warm {
  --dot-core: rgba(255, 255, 255, 1);
  --dot-glow-core: rgba(255, 255, 255, 1);
  --dot-glow-soft: rgba(255, 250, 232, 0.96);
  --dot-aura: rgba(255, 255, 255, 0.78);
  --dot-edge: rgba(94, 72, 38, 0.42);
}

.stardust-dot--cool {
  --dot-core: rgba(255, 255, 255, 1);
  --dot-glow-core: rgba(255, 255, 255, 1);
  --dot-glow-soft: rgba(235, 245, 255, 0.96);
  --dot-aura: rgba(255, 255, 255, 0.78);
  --dot-edge: rgba(64, 78, 98, 0.42);
}

@keyframes stardust-float {
  0% {
    transform: translate3d(0, 0, 0) scale(0.84);
    opacity: 0;
  }

  20% {
    opacity: var(--dot-opacity, 0.5);
  }

  50% {
    transform: translate3d(calc(var(--dot-x, 22px) * 0.45), calc(var(--dot-y, -18px) * 0.45), 0) scale(1);
    opacity: calc(var(--dot-opacity, 0.5) + 0.12);
  }

  100% {
    transform: translate3d(var(--dot-x, 22px), var(--dot-y, -18px), 0) scale(0.9);
    opacity: 0;
  }
}
</style>
