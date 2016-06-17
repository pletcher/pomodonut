self.addEventListener('message', function(e) {
  var eventType = e.data[0]
  var duration = e.data[1]

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
  self.interval = setInterval(function() {
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
