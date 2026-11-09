const mineflayer = require(process.env.MINEFLAYER_MODULE);

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: Number(process.env.FORGE_TEST_PORT),
  username: 'ForgeVerifier',
  version: '1.21.4',
  auth: 'offline',
});

bot.on('error', (error) => console.error(error.message));
bot.on('kicked', (reason) => console.error('Bağlantı kesildi:', reason));
bot.once('spawn', () => console.log('Kontrol oyuncusu bağlandı.'));
bot.on('messagestr', async (message) => {
  if (!message.includes('FORGE_ATTACK_READY')) return;
  await bot.waitForTicks(5);
  const target = bot.nearestEntity((entity) => entity.name === 'pillager');
  if (!target) throw new Error('Saldırı hedefi istemciye ulaşmadı.');
  await bot.lookAt(target.position.offset(0, 1.2, 0));
  bot.attack(target);
});
const timeout = setTimeout(() => bot.quit(), 120000);
bot.once('end', () => { clearTimeout(timeout); process.exit(0); });
