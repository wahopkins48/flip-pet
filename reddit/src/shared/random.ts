/**
 * java.util.Random, bit for bit. The phone version seeds its creature drawing,
 * names and mood lines with it, so the same seed has to give the same creature
 * here too.
 */
const MULTIPLIER = 0x5deece66dn
const MASK = (1n << 48n) - 1n
const TWO_31 = 2 ** 31

export class JavaRandom {
  #seed: bigint

  constructor(seed: number | bigint) {
    this.#seed = (BigInt.asIntN(64, BigInt(seed)) ^ MULTIPLIER) & MASK
  }

  #next(bits: number): number {
    this.#seed = (this.#seed * MULTIPLIER + 0xbn) & MASK
    return Number(BigInt.asIntN(32, this.#seed >> BigInt(48 - bits)))
  }

  nextInt(bound: number): number {
    if (!Number.isInteger(bound) || bound <= 0) throw RangeError('bad bound')
    if ((bound & -bound) === bound) {
      return Math.floor((bound * this.#next(31)) / TWO_31)
    }
    let bits: number
    let val: number
    do {
      bits = this.#next(31)
      val = bits % bound
      // Java rejects when bits - val + (bound - 1) overflows a signed int.
    } while (bits - val + (bound - 1) >= TWO_31)
    return val
  }

  nextFloat(): number {
    return this.#next(24) / (1 << 24)
  }

  nextDouble(): number {
    return (this.#next(26) * 2 ** 27 + this.#next(27)) * 2 ** -53
  }
}
