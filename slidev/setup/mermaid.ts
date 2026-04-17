/**
 * Mermaid defaults for this deck: diagrams shrink to the slide width where supported
 * (`useMaxWidth`), so zoom-out + outer pan can show the full graph instead of only
 * scrolling inside a clipped slide frame.
 */
const fit = { useMaxWidth: true as const }

export default function setupMermaid() {
  return {
    flowchart: fit,
    sequence: fit,
    class: fit,
    state: fit,
    er: fit,
    gantt: fit,
    pie: fit,
    journey: fit,
    quadrantChart: fit,
    mindmap: fit,
    timeline: fit,
    sankey: fit,
    gitGraph: fit,
    c4: fit,
    block: fit,
    packet: fit,
    architecture: fit,
    treemap: fit,
    radar: fit,
  }
}
