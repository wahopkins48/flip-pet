/**
 * Draws the creature. A port of the phone's PetView: no image assets, only
 * canvas primitives, so it can wobble, blink and sprout more eyes as it ages.
 * Everything is sized off min(width, height) so it fits any post.
 *
 * Six phenotypes come out of the one egg. Whichever hatches, the rules are the
 * same: a body that breathes, more limbs and openings each stage, and mood
 * shifts (sick, dead, asleep) that recolor it and slow it down.
 */
import {JavaRandom} from '../shared/random.ts'

export const BG = '#07040c'
const EYE_WHITE = '#f3e8ff'
const EYE_PUPIL = '#120018'
const SHUT_MOUTH = '#6b4a80'

type Palette = {
  base: string
  lit: string
  sick: string
  sickLit: string
  dead: string
  deadLit: string
  accent: string
}

/** One palette per phenotype, each with its own sick and dead shifts. */
const PALETTES: readonly Palette[] = [
  // 0 Amber Child: the first one, a purple tentacle ball.
  pal('2e1247', '4a1e6b', '3e6b2a', '5c8c3e', '231733', '33244a', 'b388ff'),
  // 1 Pale Host: a swaying column of bone on many legs, with a ringed maw.
  pal('8a8391', 'a79fb4', '6e8a5a', '8fa96f', '56525c', '6f6a78', 'd9cfb0'),
  // 2 Sleet: a dim core attended by a host of eyes that will not hold still.
  pal('16343a', '21505c', '3e5a2c', '55783f', '122229', '1a3238', '7fd9c8'),
  // 3 Red Crown: a round body with a ring of small mouths up top that all sing.
  pal('3a1428', '5a1e3e', '3f5a1e', '5c7a2e', '26101b', '341524', 'e0708a'),
  // 4 Drowned Lamp: a low wet sac, two huge eyes, and nothing but patience.
  pal('14243e', '1e3760', '2e4a1e', '45692e', '0e1726', '14202e', '7facff'),
  // 5 Old Harvest: a shaggy star of knuckled points, claws at every tip.
  pal('3a2e14', '5c4a1e', '2e3e14', '47591e', '26230f', '332e12', 'e0b070'),
]

function pal(...hex: string[]): Palette {
  const [base, lit, sick, sickLit, dead, deadLit, accent] = hex.map(
    h => `#${h}`,
  )
  return {
    base: base!,
    lit: lit!,
    sick: sick!,
    sickLit: sickLit!,
    dead: dead!,
    deadLit: deadLit!,
    accent: accent!,
  }
}

export function accentFor(phenotype: number): string {
  return PALETTES[phenotype % PALETTES.length]!.accent
}

export type Look = {
  stage: number
  phenotype: number
  asleep: boolean
  /** Dead or departed: still, shut, and grey. */
  dead: boolean
  sick: boolean
  /** 0 at laying, 1 when the egg is due to hatch, so the cracks can widen. */
  hatchProgress: number
  /** Ascended: the Elder god, drawn with a faint halo. */
  ascended: boolean
}

const TAU = Math.PI * 2
const {sin, cos, abs, min, max, PI} = Math

export class Painter {
  readonly #c: CanvasRenderingContext2D
  #look: Look
  #phase = 0

  constructor(c: CanvasRenderingContext2D, look: Look) {
    this.#c = c
    this.#look = look
  }

  set look(look: Look) {
    this.#look = look
  }

  set phase(phase: number) {
    this.#phase = phase
  }

  /** Paints into a w by h box in CSS pixels. */
  paint(w: number, h: number): void {
    const c = this.#c
    c.fillStyle = BG
    c.fillRect(0, 0, w, h)
    c.lineCap = 'round'
    c.lineJoin = 'round'
    const cx = w / 2
    const cy = h / 2
    const unit = min(w, h)
    if (this.#look.stage <= 0) this.#egg(cx, cy, unit)
    else this.#creature(cx, cy, unit)
  }

  // ------------------------------------------------------------- state

  get #stage(): number {
    return this.#look.stage
  }

  get #asleep(): boolean {
    return this.#look.asleep
  }

  get #dead(): boolean {
    return this.#look.dead
  }

  get #pal(): Palette {
    return PALETTES[this.#look.phenotype % PALETTES.length]!
  }

  #flesh(): string {
    const p = this.#pal
    return this.#dead ? p.dead : this.#look.sick ? p.sick : p.base
  }

  #fleshLit(): string {
    const p = this.#pal
    return this.#dead ? p.deadLit : this.#look.sick ? p.sickLit : p.lit
  }

  /** Accent, muted once the creature is past caring whether it looks well. */
  #accent(): string {
    if (this.#dead) return '#6a6270'
    if (this.#look.sick) return '#9fb57f'
    return this.#pal.accent
  }

  /** How fast the creature moves; sleep slows it, death stops the limbs. */
  #breathe(): number {
    return this.#asleep ? 0.35 : 1
  }

  // ---------------------------------------------------------- primitives

  #fill(color: string): void {
    this.#c.fillStyle = color
  }

  #stroke(color: string, width: number): void {
    this.#c.strokeStyle = color
    this.#c.lineWidth = max(1, width)
  }

  #circle(
    x: number,
    y: number,
    r: number,
    mode: 'fill' | 'stroke' = 'fill',
  ): void {
    const c = this.#c
    c.beginPath()
    c.arc(x, y, max(0, r), 0, TAU)
    c[mode]()
  }

  #oval(
    l: number,
    t: number,
    r: number,
    b: number,
    mode: 'fill' | 'stroke' = 'fill',
  ): void {
    const c = this.#c
    c.beginPath()
    c.ellipse(
      (l + r) / 2,
      (t + b) / 2,
      abs(r - l) / 2,
      abs(b - t) / 2,
      0,
      0,
      TAU,
    )
    c[mode]()
  }

  #line(x1: number, y1: number, x2: number, y2: number): void {
    const c = this.#c
    c.beginPath()
    c.moveTo(x1, y1)
    c.lineTo(x2, y2)
    c.stroke()
  }

  // -------------------------------------------------------------- amber

  /** The tentacle ball: the original creature, kept exactly as it was. */
  #drawAmber(cx: number, cy: number, r: number): void {
    this.#tentacles(cx, cy, r)
    this.#bodyPath(cx, cy, r)
    this.#eyes(cx, cy, r)
    this.#mouth(cx, cy, r)
  }

  #bodyPath(cx: number, cy: number, r: number): void {
    const c = this.#c
    const phase = this.#phase
    const lobes = 3 + min(this.#stage, 5) * 2
    const steps = 96
    const breath = this.#asleep ? 0.012 : 0.03
    c.beginPath()
    for (let i = 0; i <= steps; i++) {
      const angle = (i / steps) * TAU
      const wobble =
        1 +
        breath * sin(angle * lobes + phase * (this.#asleep ? 0.4 : 1.3)) +
        0.02 * sin(angle * (lobes + 3) - phase * 0.8)
      const x = cx + cos(angle) * r * wobble
      const y = cy + sin(angle) * r * wobble * 0.92
      if (i === 0) c.moveTo(x, y)
      else c.lineTo(x, y)
    }
    c.closePath()
    this.#fill(this.#flesh())
    c.fill()
    this.#stroke(this.#fleshLit(), r * 0.06)
    c.stroke()
  }

  /** Tentacles: tapered limbs that trail the motion of the body. */
  #tentacles(cx: number, cy: number, r: number): void {
    const c = this.#c
    const phase = this.#phase
    const stage = this.#stage
    const count = 2 + min(stage, 5) * 2
    const rng = new JavaRandom(stage * 977 + 13)
    for (let i = 0; i < count; i++) {
      const base =
        PI * (0.08 + (0.84 * i) / max(1, count - 1)) +
        (rng.nextFloat() - 0.5) * 0.16
      const len = r * (1.7 + rng.nextFloat() * 1.3 + min(stage, 5) * 0.22)
      const sway = sin(phase * (this.#asleep ? 0.3 : 1.1) + i * 1.7)
      const tipX = cx + cos(base) * len * (0.9 + 0.1 * sway)
      const tipY =
        cy +
        sin(base) * len * 0.85 +
        (this.#dead ? 0 : sin(phase * 0.9 + i) * r * 0.16)
      const midX =
        cx + cos(base) * len * 0.55 + cos(base + 1.57) * len * 0.3 * sway
      const midY =
        cy + sin(base) * len * 0.55 + sin(base + 1.57) * len * 0.3 * sway

      c.beginPath()
      c.moveTo(cx + cos(base) * r * 0.75, cy + sin(base) * r * 0.75)
      c.bezierCurveTo(midX, midY, midX, midY + r * 0.1, tipX, tipY)
      this.#stroke(this.#fleshLit(), r * (this.#dead ? 0.05 : 0.13))
      c.stroke()
    }
  }

  /** Eyes scattered over the body, more each stage, not blinking in unison. */
  #eyes(cx: number, cy: number, r: number): void {
    const phase = this.#phase
    const stage = this.#stage
    const count = min(1 + stage * 2, 11)
    const rng = new JavaRandom(stage * 31 + 7)
    for (let i = 0; i < count; i++) {
      const ang = rng.nextDouble() * TAU
      const dist = r * (0.15 + rng.nextFloat() * 0.5)
      const ex = cx + cos(ang) * dist
      const ey = cy + sin(ang) * dist * 0.9
      const er = r * (0.17 + rng.nextFloat() * 0.09)
      if (er < 1.6) continue

      const shut =
        this.#asleep || this.#dead || sin(phase * 0.55 + i * 2.1) > 0.99
      if (shut) {
        this.#stroke(EYE_WHITE, er * 0.28)
        this.#line(ex - er, ey, ex + er, ey)
        continue
      }
      this.#fill(EYE_WHITE)
      this.#circle(ex, ey, er)
      const px = ex + sin(phase * 0.7 + i) * er * 0.3
      const py = ey + cos(phase * 0.5 + i * 1.3) * er * 0.3
      this.#fill(EYE_PUPIL)
      this.#circle(px, py, er * 0.52)
    }
  }

  /** The mouth widens each stage, and by the end it has far too many teeth. */
  #mouth(cx: number, cy: number, r: number): void {
    const stage = this.#stage
    if (stage < 2) return
    const c = this.#c
    const w = r * (0.5 + 0.09 * (stage - 2))
    const h = r * (0.1 + 0.06 * (stage - 2))
    const my = cy + r * 0.62
    const teeth = min(3 + stage, 12)
    c.beginPath()
    c.moveTo(cx - w, my)
    for (let i = 0; i <= teeth; i++) {
      c.lineTo(cx - w + (2 * w * i) / teeth, my + (i % 2 === 0 ? 0 : h))
    }
    c.lineTo(cx + w, my)
    this.#stroke(
      this.#asleep || this.#dead ? SHUT_MOUTH : this.#accent(),
      r * 0.07,
    )
    c.stroke()
  }

  /** Two large forward-facing eyes, shared by the creatures that keep a face. */
  #frontEyes(ex: number, ey: number, er: number, gap: number): void {
    const phase = this.#phase
    for (const s of [-1, 1]) {
      const x = ex + s * gap * er
      const shut =
        this.#asleep ||
        this.#dead ||
        sin(phase * 0.6 + (s > 0 ? 0 : 1.7)) > 0.995
      if (shut) {
        this.#stroke(EYE_WHITE, er * 0.3)
        this.#line(x - er, ey, x + er, ey)
        continue
      }
      this.#fill(EYE_WHITE)
      this.#circle(x, ey, er)
      const px = x + sin(phase * 0.7 + s) * er * 0.28
      const py = ey + cos(phase * 0.5 + s * 1.3) * er * 0.28
      this.#fill(EYE_PUPIL)
      this.#circle(px, py, er * 0.55)
    }
  }

  /** A wide low mouth with a jagged tooth line. */
  #wideMouth(cx: number, my: number, w: number, yaw: number): void {
    const c = this.#c
    const teeth = 4 + this.#stage
    c.beginPath()
    c.moveTo(cx - w, my)
    for (let i = 0; i <= teeth; i++) {
      c.lineTo(cx - w + (2 * w * i) / teeth, my + (i % 2 === 0 ? 0 : yaw))
    }
    c.lineTo(cx + w, my)
    this.#stroke(
      this.#asleep || this.#dead ? SHUT_MOUTH : this.#accent(),
      w * 0.1,
    )
    c.stroke()
  }

  // ---------------------------------------------------------- pale host

  /** A swaying spire of bone on many legs, its hood a single ringed mouth. */
  #drawPaleHost(cx: number, cy: number, r: number): void {
    const c = this.#c
    const phase = this.#phase
    const stage = this.#stage
    const sway = this.#breathe() * sin(phase * 1.1) * r * 0.06
    const legs = 6 + min(stage, 5) * 2
    for (let i = 0; i < legs; i++) {
      const t = i / max(1, legs - 1)
      const a = PI * (0.18 + 0.64 * t)
      const hipX = cx + cos(a) * r * 0.72
      const hipY = cy + sin(a) * r * 0.4 - r * 0.05
      const lift = this.#dead ? 0 : 0.5 + 0.5 * sin(phase * 1.3 + i * 1.9)
      const knY = hipY + r * (0.35 + 0.3 * lift)
      const footX = hipX + cos(a) * (r * 0.55 + 0.3 * r * lift)
      const footY = cy + r * (1.05 - 0.12 * lift)
      c.beginPath()
      c.moveTo(hipX, hipY)
      c.quadraticCurveTo(hipX + cos(a) * r * 0.25, knY, footX, footY)
      this.#stroke(this.#fleshLit(), r * 0.05)
      c.stroke()
      this.#fill(this.#flesh())
      this.#circle(footX, footY, r * 0.035)
    }

    // The column: a thick curved spine, then three ribs.
    const topY = cy - r * 1.15
    this.#stroke(this.#flesh(), r * 0.34)
    c.beginPath()
    c.moveTo(cx + sway, topY + r * 0.4)
    c.quadraticCurveTo(cx + sway * 0.6, cy, cx + sway * 0.4, cy + r * 0.85)
    c.stroke()
    this.#stroke(this.#fleshLit(), r * 0.05)
    for (let i = 0; i < 3; i++) {
      const t = i - 1
      const py = cy + t * r * 0.55
      const px = cx + sway * (0.35 + 0.3 * (1 - abs(t)))
      const w = r * (0.34 - 0.1 * abs(t))
      c.beginPath()
      c.moveTo(px - w, py)
      c.quadraticCurveTo(px, py - r * 0.16, px + w, py)
      c.stroke()
    }

    // The hood: a head taller than the column, with the maw in it.
    const hy = topY + r * 0.05
    const hw = r * 0.46
    this.#fill(this.#flesh())
    this.#oval(cx + sway - hw, hy - r * 0.6, cx + sway + hw, hy + r * 0.6)
    this.#stroke(this.#fleshLit(), r * 0.05)
    this.#oval(
      cx + sway - hw,
      hy - r * 0.6,
      cx + sway + hw,
      hy + r * 0.6,
      'stroke',
    )

    // The maw opens with each breath, ringed by small pointed teeth.
    const open =
      this.#dead || this.#asleep
        ? 0.12
        : 0.45 + 0.22 * this.#breathe() * abs(sin(phase * 1.1))
    const mw = r * 0.1
    const mh = r * 0.45 * open
    this.#fill(EYE_PUPIL)
    this.#oval(cx + sway - mw, hy - mh, cx + sway + mw, hy + mh)
    const teeth = 4 + min(stage, 5)
    this.#stroke(
      this.#asleep || this.#dead ? SHUT_MOUTH : this.#accent(),
      r * 0.035,
    )
    for (let i = 0; i < teeth; i++) {
      const a = PI * (0.1 + (0.8 * i) / max(1, teeth - 1))
      this.#line(
        cx + sway + cos(a) * r * 0.26,
        hy + r * 0.34,
        cx + sway + cos(a) * r * 0.16,
        hy + r * 0.4,
      )
    }

    // Two slit eyes above the maw.
    for (const s of [-1, 1]) {
      const ex = cx + sway + s * r * 0.17
      const ey = hy - r * 0.34
      const shut = this.#asleep || this.#dead || sin(phase * 0.5 + s) > 0.99
      if (shut) {
        this.#stroke(EYE_WHITE, r * 0.04)
        this.#line(ex - r * 0.05, ey, ex + r * 0.05, ey)
      } else {
        this.#fill(EYE_WHITE)
        this.#oval(ex - r * 0.045, ey - r * 0.11, ex + r * 0.045, ey + r * 0.11)
        this.#fill(EYE_PUPIL)
        this.#circle(ex, ey - r * 0.03, r * 0.028)
      }
    }
  }

  // -------------------------------------------------------------- sleet

  /** A dim core with a host of separate eyes orbiting it, none of them staying. */
  #drawSleet(cx: number, cy: number, r: number): void {
    const phase = this.#phase
    const stage = this.#stage
    const n = 6 + min(stage, 5) * 3
    const rng = new JavaRandom(this.#look.phenotype * 2039 + stage * 37 + 5)
    const spin = this.#dead ? 0 : this.#asleep ? 0.05 : 0.25
    for (let i = 0; i < n; i++) {
      const a = (i / n) * TAU + phase * spin
      const d = r * (1.3 + 0.08 * sin(i * 2.7 + phase * 0.6))
      const mx = cx + cos(a) * d
      const my = cy + sin(a) * d * 0.8
      const er =
        r * (0.085 + 0.012 * rng.nextFloat()) * (1 + 0.08 * min(stage, 5))
      if (this.#dead) {
        this.#fill('#2a3340')
        this.#circle(mx, cy + r * 1.1, max(1, er * 0.4))
        continue
      }
      const flit = sin(phase * 0.8 + i * 1.77) > 0.985 && !this.#asleep
      if (this.#asleep || flit) {
        this.#stroke('#6e9aa0', er * 0.3)
        this.#line(mx - er, my, mx + er, my)
        continue
      }
      this.#fill(EYE_WHITE)
      this.#circle(mx, my, er)
      this.#fill(EYE_PUPIL)
      this.#circle(mx + cos(a) * er * 0.35, my + sin(a) * er * 0.35, er * 0.5)
    }

    this.#fill(this.#flesh())
    this.#circle(cx, cy, r * 0.42)
    this.#stroke(this.#fleshLit(), r * 0.05)
    this.#circle(cx, cy, r * 0.42, 'stroke')
    const coreOpen = this.#dead
      ? 0.05
      : this.#asleep
        ? 0.1
        : 0.35 + 0.15 * this.#breathe() * sin(phase * 1.3)
    this.#stroke(this.#accent(), r * 0.05)
    this.#line(cx, cy - r * 0.3 * coreOpen, cx, cy + r * 0.3 * coreOpen)
  }

  // ---------------------------------------------------------- red crown

  /** A round body whose top edge is a ring of openings, each one singing. */
  #drawCrown(cx: number, cy: number, r: number): void {
    const c = this.#c
    const phase = this.#phase
    const stage = this.#stage
    this.#bodyPath(cx, cy, r * 0.94)

    const k = 4 + min(stage, 5) * 2
    for (let i = 0; i < k; i++) {
      const frac = 0.04 + (0.92 * i) / max(1, k - 1)
      const a = PI * (1 - frac)
      const mx = cx + cos(a) * r * 1.02
      const my = cy + sin(a) * r * 0.92
      const open =
        this.#dead || this.#asleep
          ? 0.1
          : 0.55 + 0.22 * this.#breathe() * abs(sin(phase * 1.3 + i * 1.1))
      const mr = r * 0.13 * open
      this.#fill(EYE_PUPIL)
      this.#circle(mx, my, max(0.5, mr))
      this.#stroke(this.#dead ? this.#fleshLit() : this.#accent(), r * 0.04)
      this.#circle(mx, my, max(0.5, mr * 1.35), 'stroke')
      if (open > 0.55) {
        this.#fill(this.#accent())
        this.#circle(mx, my + mr * 0.5, max(0.5, mr * 0.22))
      }
    }

    const w = 3 + min(stage, 4)
    const rng = new JavaRandom(this.#look.phenotype * 101 + stage * 13 + 7)
    for (let i = 0; i < w; i++) {
      const a = PI * (0.14 + 0.72 * rng.nextFloat())
      const wx = cx + cos(a) * r * 0.85
      const wy = cy + sin(a) * r * 0.8
      const sway = this.#dead ? 0 : sin(phase * 1.1 + i * 1.9) * r * 0.16
      const tipX = wx + sway
      const tipY = wy + r * (0.9 + rng.nextFloat() * 0.5)
      c.beginPath()
      c.moveTo(wx, wy)
      c.quadraticCurveTo(
        wx + sin(phase * 0.9 + i) * r * 0.1,
        wy + r * 0.45,
        tipX,
        tipY,
      )
      this.#stroke(this.#fleshLit(), r * 0.045)
      c.stroke()
    }

    this.#frontEyes(cx, cy - r * 0.18, r * 0.15, 0.62)
    this.#mouth(cx, cy, r)
  }

  // ------------------------------------------------------- drowned lamp

  /** A wide wet sac: two enormous eyes, one low wide mouth, very long patience. */
  #drawLamp(cx: number, cy: number, r: number): void {
    const c = this.#c
    const phase = this.#phase
    const sway = this.#dead
      ? 0
      : this.#breathe() * 0.5 * sin(phase * 0.4) * r * 0.05
    cx += sway
    const rx = r * 1.02
    const ry = r * 0.6
    const bodyCx = cx
    const bodyCy = cy + r * 0.08
    this.#fill(this.#flesh())
    this.#oval(bodyCx - rx, bodyCy - ry, bodyCx + rx, bodyCy + ry)
    this.#stroke(this.#fleshLit(), r * 0.05)
    this.#oval(bodyCx - rx, bodyCy - ry, bodyCx + rx, bodyCy + ry, 'stroke')
    this.#stroke(this.#fleshLit(), r * 0.055)
    this.#line(
      bodyCx - rx * 0.55,
      bodyCy - ry * 0.55,
      bodyCx + rx * 0.55,
      bodyCy - ry * 0.55,
    )

    for (const s of [-1, 1]) {
      const baseX = bodyCx + s * r * 0.6
      const baseY = bodyCy - ry * 0.6
      const tipX =
        baseX + s * r * 0.2 + (this.#dead ? 0 : sin(phase * 0.8 + s) * r * 0.1)
      const tipY = baseY - r * 0.55
      c.beginPath()
      c.moveTo(baseX, baseY)
      c.quadraticCurveTo(baseX + s * r * 0.15, baseY - r * 0.3, tipX, tipY)
      this.#stroke(this.#fleshLit(), r * 0.035)
      c.stroke()
    }

    this.#frontEyes(bodyCx, bodyCy - ry * 0.4, r * 0.2, 0.55)

    const yn = r * (0.45 + 0.05 * min(this.#stage, 5))
    const yawn =
      this.#dead || this.#asleep
        ? r * 0.03
        : r * (0.09 + 0.06 * this.#breathe() * abs(sin(phase * 0.9)))
    this.#wideMouth(bodyCx, bodyCy + ry * 0.55, yn, max(1, yawn))
  }

  // -------------------------------------------------------- old harvest

  /** A spiky star of knuckled points, claws at every tip, eyes near the heart. */
  #drawHarvest(cx: number, cy: number, r: number): void {
    const c = this.#c
    const phase = this.#phase
    const pts = 5 + min(this.#stage, 5)
    const rot = this.#dead ? 0 : phase * (this.#asleep ? 0.03 : 0.1)
    c.beginPath()
    for (let i = 0; i < pts * 2; i++) {
      const outer = i % 2 === 0
      let rad = outer ? r : r * 0.5
      const ang = rot + (i * PI) / pts
      rad *= 1 + 0.05 * sin(ang * 3 + phase * (this.#dead ? 0 : 1.4))
      const x = cx + cos(ang) * rad
      const y = cy + sin(ang) * rad
      if (i === 0) c.moveTo(x, y)
      else c.lineTo(x, y)
    }
    c.closePath()
    this.#fill(this.#flesh())
    c.fill()
    this.#stroke(this.#fleshLit(), r * 0.05)
    c.stroke()

    for (let i = 0; i < pts; i++) {
      const ang = rot + (2 * i * PI) / pts
      const tipX = cx + cos(ang) * r * 1.05
      const tipY = cy + sin(ang) * r * 1.05
      const shiver = this.#dead ? 0 : sin(phase * 2.3 + i * 2.1) * r * 0.03
      for (const k of [-1, 1]) {
        const ca = ang + k * 0.5 + shiver / max(r, 1)
        this.#stroke(this.#dead ? this.#fleshLit() : this.#accent(), r * 0.045)
        this.#line(
          tipX,
          tipY,
          tipX + cos(ca) * r * 0.18,
          tipY + sin(ca) * r * 0.18,
        )
      }
    }

    for (let i = 0; i < 3; i++) {
      const ang = -PI / 2 + i * 0.9
      const ex = cx + cos(ang) * r * 0.22
      const ey = cy + sin(ang) * r * 0.22
      const er = r * 0.075
      const shut =
        this.#asleep || this.#dead || sin(phase * 0.55 + i * 2.1) > 0.985
      if (shut) {
        this.#stroke(EYE_WHITE, er * 0.3)
        this.#line(ex - er, ey, ex + er, ey)
      } else {
        this.#fill(EYE_WHITE)
        this.#circle(ex, ey, er)
        this.#fill(EYE_PUPIL)
        this.#circle(
          ex + sin(phase * 0.7 + i) * er * 0.3,
          ey + cos(phase * 0.5 + i * 1.3) * er * 0.3,
          er * 0.5,
        )
      }
    }

    const open = this.#dead
      ? r * 0.02
      : this.#asleep
        ? r * 0.06
        : r * (0.18 + 0.1 * this.#breathe() * abs(sin(phase * 1.2)))
    this.#stroke(this.#accent(), r * 0.05)
    this.#line(cx, cy - open, cx, cy + open)
  }

  // --------------------------------------------------------------- body

  #creature(cx: number, cy: number, unit: number): void {
    const r = unit * (0.15 + min(this.#stage, 5) * 0.022)
    if (this.#look.ascended) this.#halo(cx, cy, r)
    switch (this.#look.phenotype % PALETTES.length) {
      case 5:
        this.#drawHarvest(cx, cy, r)
        break
      case 4:
        this.#drawLamp(cx, cy, r)
        break
      case 3:
        this.#drawCrown(cx, cy, r)
        break
      case 2:
        this.#drawSleet(cx, cy, r)
        break
      case 1:
        this.#drawPaleHost(cx, cy, r)
        break
      default:
        this.#drawAmber(cx, cy, r)
    }
    if (this.#asleep && !this.#dead) this.#snore(cx, cy, r, unit)
  }

  /** An ascended Elder god: a slow, faint ring of its own light behind it. */
  #halo(cx: number, cy: number, r: number): void {
    const c = this.#c
    const pulse = 0.5 + 0.5 * sin(this.#phase * 0.3)
    c.save()
    c.globalAlpha = 0.18 + 0.12 * pulse
    this.#stroke(this.#pal.accent, r * 0.08)
    this.#circle(cx, cy, r * (1.45 + 0.04 * pulse), 'stroke')
    c.globalAlpha = 0.08 + 0.06 * pulse
    this.#stroke(this.#pal.accent, r * 0.04)
    this.#circle(cx, cy, r * 1.7, 'stroke')
    c.restore()
  }

  /** Three drifting z's above a sleeping pet. */
  #snore(cx: number, cy: number, r: number, unit: number): void {
    const c = this.#c
    this.#fill(this.#accent())
    c.font = `${unit * 0.075}px ui-monospace, monospace`
    const x = cx + r * 1.15
    const y = cy - r * 1.05
    for (let i = 0; i < 3; i++) {
      const drift = sin(this.#phase * 0.5 + i) * unit * 0.03
      c.fillText('z', max(0, x + drift), y - i * unit * 0.07)
    }
  }

  /** Stage 0: an unthought egg, cracking as the hatchling nears. All six share it. */
  #egg(cx: number, cy: number, unit: number): void {
    const c = this.#c
    const r = unit * 0.17
    this.#fill('#d9cfb0')
    this.#oval(cx - r * 0.8, cy - r, cx + r * 0.8, cy + r)
    this.#stroke('#8a7a55', r * 0.08)
    this.#oval(cx - r * 0.8, cy - r, cx + r * 0.8, cy + r, 'stroke')

    const progress = this.#look.hatchProgress
    this.#stroke('#4a3b22', r * 0.07)
    const rng = new JavaRandom(99)
    for (let i = 0; i < 4; i++) {
      const a = -1.6 + i * 0.8 + rng.nextFloat() * 0.2
      c.beginPath()
      c.moveTo(cx + cos(a) * r * 0.7, cy + sin(a) * r)
      for (let k = 1; k <= 4; k++) {
        const rr = r * (0.7 + k * 0.09) * progress
        c.lineTo(cx + cos(a + k * 0.25) * rr, cy + sin(a + k * 0.25) * rr)
      }
      c.stroke()
    }
    if (progress > 0.6 && !this.#dead) {
      const ex = cx - r * 0.22
      const ey = cy + r * 0.1
      const er = r * 0.14
      this.#fill(EYE_WHITE)
      this.#circle(ex, ey, er)
      this.#circle(ex + r * 0.44, ey, er)
      this.#fill(EYE_PUPIL)
      this.#circle(ex, ey, er * 0.5)
      this.#circle(ex + r * 0.44, ey, er * 0.5)
    }
  }
}
