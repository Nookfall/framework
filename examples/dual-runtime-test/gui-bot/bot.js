'use strict'
// Joins a test server and drives the ProbeGui plugin's menus with real inventory clicks.
// Usage: node bot.js <host> <port>
const mineflayer = require('mineflayer')
const { once } = require('events')

const [host, port] = process.argv.slice(2)
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const log = (...args) => console.log('[bot]', ...args)

const bot = mineflayer.createBot({ host, port: Number(port), username: 'NookfallBot', auth: 'offline' })
const timeout = setTimeout(() => { log('timed out'); process.exit(2) }, 90000)

bot.on('kicked', reason => { log('kicked', JSON.stringify(reason)); process.exit(3) })
bot.on('error', error => { log('error', error.message); process.exit(4) })
bot.on('messagestr', message => log('chat:', message))

async function command (text) {
  bot.chat(text)
  await sleep(700)
}

async function openMenu (text) {
  const opened = once(bot, 'windowOpen')
  bot.chat(text)
  const [window] = await opened
  await sleep(500)
  return window
}

async function click (window, slot, button, mode, label) {
  log('click', label, 'slot', slot, 'button', button, 'mode', mode)
  try {
    await bot.clickWindow(slot, button, mode)
  } catch (error) {
    log('click error (expected for rejected clicks):', error.message)
  }
  await sleep(400)
  if (bot._syncWindow && bot.currentWindow) await bot._syncWindow(bot.currentWindow).catch(() => {})
}

function name (item) {
  if (!item) return null
  return item.customName ?? item.displayName ?? item.name
}

// Raw drag sequence: start, add slots, end. The server should cancel it.
function drag (window, slots) {
  const Item = require('prismarine-item')(bot.registry)
  const raw = (slot, button) => bot._client.write('window_click', {
    windowId: window.id, stateId: -1, slot, mouseButton: button, mode: 5, changedSlots: [],
    cursorItem: Item.toNotch(window.selectedItem)
  })
  raw(-999, 0)
  for (const slot of slots) raw(slot, 1)
  raw(-999, 2)
}

bot.once('spawn', async () => {
  try {
    log('spawned on', bot.version)
    await sleep(1500)
    await command('/guiprobe prepare')

    let window = await openMenu('/guiprobe open')
    log('opened', JSON.stringify(window.title), 'slots', window.inventoryStart)
    log('slot 9', name(window.slots[9]), 'slot 26', name(window.slots[26]))

    await click(window, 10, 0, 0, 'left n2')
    await click(window, 11, 1, 0, 'right n3')
    await click(window, 12, 0, 1, 'shift-left n4')
    await click(window, 13, 0, 2, 'number-key on n5')

    const diamonds = window.inventoryStart // player main inventory slot 9 is the first bottom slot
    await click(window, diamonds, 0, 1, 'shift-click diamonds from own inventory')
    await click(window, diamonds, 0, 0, 'pick up diamonds')
    drag(window, [3, 4, 5])
    await sleep(600)
    if (bot._syncWindow) await bot._syncWindow(window).catch(() => {})
    await click(window, diamonds, 0, 0, 'put diamonds back')
    await command('/guiprobe check-protection')

    await click(window, 26, 0, 0, 'next page')
    log('after next page slot 9', name(window.slots[9]))
    await click(window, 9, 0, 0, 'left first entry of page 2')

    await command('/guiprobe reload-move')
    await sleep(500)
    window = bot.currentWindow
    log('after reload slot 0', name(window.slots[0]))
    await click(window, 0, 0, 0, 'left first entry after layout moved')

    await command('/guiprobe reload-broken')
    await click(window, 0, 0, 0, 'left first entry after rejected reload')

    const closed = once(bot, 'windowClose')
    await click(window, 22, 0, 0, 'close button')
    await Promise.race([closed, sleep(3000)])
    log('window after close', bot.currentWindow ? 'open' : 'closed')

    await openMenu('/guiprobe slow')
    log('slow menu opened; closing before its provider finishes')
    bot.closeWindow(bot.currentWindow)
    await sleep(3500)
    log('window after slow provider finished', bot.currentWindow ? 'open' : 'closed')

    await openMenu('/guiprobe open')
    log('quitting with the menu open')
    clearTimeout(timeout)
    bot.quit()
    await sleep(500)
    process.exit(0)
  } catch (error) {
    log('failed', error.stack)
    process.exit(1)
  }
})
