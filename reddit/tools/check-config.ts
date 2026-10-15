// Validates devvit.json with the same parser the Devvit CLI uses before
// playtest, upload and publish, so a bad config fails `npm test` instead.
import {readFileSync} from 'node:fs'
import {parseAppConfig} from '@devvit/shared-types/schemas/config-file.v1.js'

const config = parseAppConfig(readFileSync('devvit.json', 'utf8'))
console.log(`devvit.json ok: ${config.name}`)
