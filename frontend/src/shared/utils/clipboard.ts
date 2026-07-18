type ClipboardWriter = Pick<Clipboard, 'writeText'>

export async function copyText(
  text: string,
  clipboard: ClipboardWriter | undefined = globalThis.navigator?.clipboard,
  documentRef: Document = globalThis.document,
) {
  if (clipboard) {
    try {
      await clipboard.writeText(text)
      return true
    } catch {
      // HTTP 교실망처럼 Clipboard API가 막힌 환경에서는 아래 방식으로 다시 시도합니다.
    }
  }

  const textarea = documentRef.createElement('textarea')
  textarea.value = text
  textarea.readOnly = true
  textarea.setAttribute('aria-hidden', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.opacity = '0'
  documentRef.body.appendChild(textarea)

  try {
    textarea.focus()
    textarea.select()
    const executeCopy = documentRef.execCommand
    return typeof executeCopy === 'function' && executeCopy.call(documentRef, 'copy')
  } catch {
    return false
  } finally {
    textarea.remove()
  }
}
