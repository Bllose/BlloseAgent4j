async function canvasHash() {
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 280
    canvas.height = 60
    const ctx = canvas.getContext('2d')
    ctx.textBaseline = 'top'
    ctx.font = '14px Arial'
    ctx.fillStyle = '#f60'
    ctx.fillRect(125, 1, 62, 20)
    ctx.fillStyle = '#069'
    ctx.fillText('BlloseAgent4J <canvas> 1.0', 2, 15)
    ctx.fillStyle = 'rgba(102, 204, 0, 0.7)'
    ctx.fillText('BlloseAgent4J <canvas> 1.0', 4, 36)

    const data = canvas.toDataURL()
    const encoder = new TextEncoder()
    const hashBuffer = await crypto.subtle.digest('SHA-256', encoder.encode(data))
    return Array.from(new Uint8Array(hashBuffer))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('')
      .slice(0, 32)
  } catch {
    return 'no-canvas'
  }
}

export async function generateFingerprint() {
  const components = {
    userAgent: navigator.userAgent || 'unknown',
    platform: navigator.platform || 'unknown',
    hardwareConcurrency: navigator.hardwareConcurrency || 'unknown',
    screen: `${screen.width}x${screen.height}x${screen.colorDepth}`,
    language: navigator.language || 'unknown',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'unknown',
    canvas: await canvasHash()
  }

  const raw = Object.values(components).join('|')
  const encoder = new TextEncoder()
  const hashBuffer = await crypto.subtle.digest('SHA-256', encoder.encode(raw))
  const fingerprint = Array.from(new Uint8Array(hashBuffer))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')

  return {
    fingerprint,
    userAgent: components.userAgent,
    platform: components.platform,
    screenInfo: components.screen,
    language: components.language,
    timezone: components.timezone,
    hostname: ''
  }
}
