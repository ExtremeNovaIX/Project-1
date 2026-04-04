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
    0 0 10px var(--dot-glow-core),
    0 0 var(--dot-glow, 16px) var(--dot-glow-soft);
}

.stardust-dot::after {
  width: calc(100% + 10px);
  height: calc(100% + 10px);
  background: radial-gradient(circle, var(--dot-aura) 0%, rgba(255, 255, 255, 0) 72%);
  opacity: 0.85;
}

.stardust-dot--warm {
  --dot-core: rgba(255, 255, 255, 0.96);
  --dot-glow-core: rgba(255, 248, 218, 0.95);
  --dot-glow-soft: rgba(241, 172, 108, 0.3);
  --dot-aura: rgba(255, 238, 196, 0.42);
}

.stardust-dot--cool {
  --dot-core: rgba(255, 255, 255, 0.98);
  --dot-glow-core: rgba(229, 248, 255, 0.98);
  --dot-glow-soft: rgba(125, 211, 252, 0.24);
  --dot-aura: rgba(186, 230, 253, 0.34);
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
