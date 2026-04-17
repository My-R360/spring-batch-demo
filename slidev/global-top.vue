<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { onClickOutside, onKeyStroke, useEventListener, useLocalStorage } from '@vueuse/core'
import { useNav } from '@slidev/client/composables/useNav.ts'
import type { SlideRoute } from '@slidev/types'

const { slides, currentSlideNo, go, isPrintMode } = useNav()

/** Extra zoom on #slide-content (CSS `zoom`); Ctrl/⌘ or Alt + wheel, or +/- buttons */
const ZOOM_MIN = 0.25 /** zoom out = 1/4 scale → see ~4× the canvas area vs 100% */
const ZOOM_MAX = 2 /** zoom in at most 2× */

const userZoom = useLocalStorage('slidev-deck-user-zoom', 1)

function applyUserZoom() {
  document.documentElement.style.setProperty('--deck-user-zoom', String(userZoom.value))
}

watch(userZoom, applyUserZoom, { immediate: true })

const zoomPct = computed(() => Math.round(userZoom.value * 100))

function clampZoom(v: number) {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, v))
}

function zoomIn() {
  userZoom.value = clampZoom(+(userZoom.value * 1.12).toFixed(3))
}

function zoomOut() {
  userZoom.value = clampZoom(+(userZoom.value / 1.12).toFixed(3))
}

function zoomReset() {
  userZoom.value = 1
}

function isZoomWheelModifier(e: WheelEvent) {
  return e.ctrlKey || e.metaKey || e.altKey
}

onMounted(() => {
  return useEventListener(
    window,
    'wheel',
    (e: WheelEvent) => {
      if (isPrintMode.value)
        return
      if (!isZoomWheelModifier(e))
        return
      const el = e.target as HTMLElement | null
      if (el?.closest('input, textarea, select, [contenteditable="true"]'))
        return
      e.preventDefault()
      const factor = e.deltaY < 0 ? 1.06 : 1 / 1.06
      userZoom.value = clampZoom(+(userZoom.value * factor).toFixed(4))
    },
    { passive: false, capture: true },
  )
})

const open = ref(false)
const host = ref<HTMLElement | null>(null)

onClickOutside(host, () => {
  open.value = false
})

onKeyStroke('Escape', (e) => {
  if (!open.value)
    return
  e.preventDefault()
  open.value = false
})

watch(
  () => currentSlideNo.value,
  () => {
    open.value = false
  },
)

const entries = computed(() =>
  slides.value
    .filter((r: SlideRoute) => {
      const fm = r.meta?.slide?.frontmatter
      return !fm?.hide && !fm?.disabled
    })
    .map((r: SlideRoute) => {
      const raw = r.meta?.slide?.title
      const title = typeof raw === 'string' && raw.trim().length > 0 ? raw.trim() : `Slide ${r.no}`
      return { no: r.no, title }
    }),
)

async function jump(no: number) {
  await go(no, 0)
  open.value = false
}

function toggle() {
  if (isPrintMode.value)
    return
  open.value = !open.value
}
</script>

<template>
  <div
    v-show="!isPrintMode"
    class="zoom-host"
    title="Zoom the slide canvas (incl. diagrams): Ctrl/⌘ or Alt + pinch/scroll, or − / +. Range 25%–200%. Tall diagrams: pan the slide area (scrollbars) after zooming out."
  >
    <button type="button" class="zoom-icon" aria-label="Zoom out" @click="zoomOut">−</button>
    <span class="zoom-pct">{{ zoomPct }}%</span>
    <button type="button" class="zoom-icon" aria-label="Zoom in" @click="zoomIn">+</button>
    <button type="button" class="zoom-reset" aria-label="Reset zoom to 100%" @click="zoomReset">
      Reset
    </button>
  </div>

  <div
    v-show="!isPrintMode"
    ref="host"
    class="toc-host"
    aria-label="Slide outline"
  >
    <button
      type="button"
      class="toc-btn"
      :aria-expanded="open"
      aria-controls="slidev-contents-panel"
      @click="toggle"
    >
      Contents
    </button>
    <Transition name="toc-fade">
      <div
        v-if="open"
        id="slidev-contents-panel"
        class="toc-panel"
        role="menu"
      >
        <div class="toc-panel-header">Slides</div>
        <ul class="toc-list" role="none">
          <li
            v-for="item in entries"
            :key="item.no"
            role="none"
          >
            <button
              type="button"
              class="toc-item"
              :class="{ 'toc-item--active': item.no === currentSlideNo }"
              role="menuitem"
              @click="jump(item.no)"
            >
              <span class="toc-item-no">{{ item.no }}</span>
              <span class="toc-item-title">{{ item.title }}</span>
            </button>
          </li>
        </ul>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.zoom-host {
  position: fixed;
  top: 0.75rem;
  left: 0.75rem;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem 0.35rem;
  border-radius: 0.375rem;
  border: 1px solid rgba(156, 163, 175, 0.35);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  font-family: Avenir Next, Nunito Sans, ui-sans-serif, system-ui, sans-serif;
  font-size: 0.75rem;
}

html.dark .zoom-host {
  background: rgba(24, 24, 24, 0.92);
  border-color: rgba(156, 163, 175, 0.35);
  color: #e5e5e5;
}

.zoom-icon {
  cursor: pointer;
  border: none;
  background: transparent;
  color: inherit;
  font-size: 1rem;
  line-height: 1;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 0.25rem;
  padding: 0;
}

.zoom-icon:hover {
  background: rgba(58, 185, 213, 0.15);
  color: var(--slidev-theme-primary, #3ab9d5);
}

.zoom-pct {
  min-width: 2.75rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
  opacity: 0.85;
  font-size: 0.7rem;
}

.zoom-reset {
  cursor: pointer;
  margin-left: 0.15rem;
  border: none;
  border-radius: 0.25rem;
  padding: 0.15rem 0.4rem;
  font-size: 0.65rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: inherit;
  background: rgba(156, 163, 175, 0.15);
}

.zoom-reset:hover {
  background: rgba(58, 185, 213, 0.2);
  color: var(--slidev-theme-primary, #3ab9d5);
}

.toc-host {
  position: fixed;
  top: 0.75rem;
  right: 0.75rem;
  z-index: 100;
  font-family: Avenir Next, Nunito Sans, ui-sans-serif, system-ui, sans-serif;
  font-size: 0.875rem;
  line-height: 1.25;
}

.toc-btn {
  cursor: pointer;
  border-radius: 0.375rem;
  border: 1px solid rgba(156, 163, 175, 0.35);
  padding: 0.35rem 0.75rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #181818;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(6px);
}

html.dark .toc-btn {
  color: #e5e5e5;
  background: rgba(24, 24, 24, 0.92);
  border-color: rgba(156, 163, 175, 0.35);
}

.toc-btn:hover {
  border-color: var(--slidev-theme-primary, #3ab9d5);
  color: var(--slidev-theme-primary, #3ab9d5);
}

.toc-panel {
  margin-top: 0.35rem;
  max-height: min(70vh, 28rem);
  width: min(22rem, calc(100vw - 1.5rem));
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-radius: 0.375rem;
  border: 1px solid rgba(156, 163, 175, 0.35);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

html.dark .toc-panel {
  background: rgba(18, 18, 18, 0.98);
  border-color: rgba(156, 163, 175, 0.35);
}

.toc-panel-header {
  flex-shrink: 0;
  padding: 0.5rem 0.75rem;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.55;
  border-bottom: 1px solid rgba(156, 163, 175, 0.25);
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0.25rem 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.toc-item {
  display: flex;
  width: 100%;
  align-items: flex-start;
  gap: 0.5rem;
  text-align: left;
  cursor: pointer;
  border: none;
  background: transparent;
  padding: 0.45rem 0.75rem;
  color: inherit;
  font: inherit;
}

.toc-item:hover {
  background: rgba(58, 185, 213, 0.12);
}

.toc-item--active {
  background: rgba(58, 185, 213, 0.18);
  font-weight: 600;
}

.toc-item-no {
  flex-shrink: 0;
  min-width: 1.5rem;
  opacity: 0.45;
  font-variant-numeric: tabular-nums;
}

.toc-item-title {
  flex: 1;
  word-break: break-word;
}

.toc-fade-enter-active,
.toc-fade-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}

.toc-fade-enter-from,
.toc-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
