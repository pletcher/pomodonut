self.addEventListener('message', e => {
  const [eventType, duration] = e.data

  switch (eventType) {
  case 'start':
    startTick(duration, 0)
    break
  case 'stop':
    clearInterval(self.interval)
  default:
    break
  }
})

function postTick() {
  self.postMessage('tick')
}

function startTick(duration, elapsed) {
  self.interval = setInterval(() => {
    if (++elapsed < duration) {
      postTick()
    } else {
      clearInterval(self.interval)
      stopTick()
    }
  }, 1000)
}

function stopTick() {
  self.postMessage('stop')
}
