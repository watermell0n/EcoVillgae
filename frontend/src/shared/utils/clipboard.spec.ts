import { afterEach, describe, expect, it, vi } from 'vitest'

import { copyText } from './clipboard'

afterEach(() => {
  vi.restoreAllMocks()
  Reflect.deleteProperty(document, 'execCommand')
})

describe('copyText', () => {
  it('uses the Clipboard API when it is available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)

    await expect(copyText('ECO-01', { writeText })).resolves.toBe(true)
    expect(writeText).toHaveBeenCalledWith('ECO-01')
  })

  it('falls back to a selected textarea when the Clipboard API is blocked', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('blocked'))
    const execCommand = vi.fn().mockReturnValue(true)
    Object.defineProperty(document, 'execCommand', {
      configurable: true,
      value: execCommand,
    })

    await expect(copyText('ECO-02', { writeText })).resolves.toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(document.querySelector('textarea[aria-hidden="true"]')).toBeNull()
  })
})
