package app.shining.server.http

import app.shining.server.log.Log
import app.shining.server.watch.SamadSignIn
import app.shining.server.watch.SignInResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The sign-in page: `GET /v1/debug?signin`. Samad wants a captcha for every password login, so
 * when the server has no working session, the admin opens this page (the admin alert links to it). The
 * server fetches the captcha from Samad itself, the admin types what it says, and the server logs in from
 * its own address. The page holds nothing secret; every action is a `POST /v1/debug` with the
 * debug token in a header, which the page asks for once and keeps in the phone's browser.
 *
 * It lives on `/v1/debug` because nginx only lets that path, `/v1/status` and `/health` through.
 */
@Serializable
data class SignInRequest(val action: String, val nonce: String = "", val text: String = "")

@Serializable
data class CaptchaResponse(val needed: Boolean, val nonce: String, val image: String)

@Serializable
private data class ErrorResponse(val message: String)

internal suspend fun ApplicationCall.respondSignInPage() {
    response.header("Cache-Control", "no-store")
    response.header("X-Frame-Options", "DENY")
    respondText(SIGN_IN_HTML, ContentType.Text.Html.withParameter("charset", "utf-8"))
}

/** Handles one sign-in action from the page. The token was already checked. */
internal suspend fun ApplicationCall.handleSignIn(
    signIn: SamadSignIn,
    body: String,
    json: Json,
    onSignedIn: () -> Unit,
) {
    response.header("Cache-Control", "no-store")
    val request = runCatching { lenientJson.decodeFromString(SignInRequest.serializer(), body) }.getOrNull()
        ?: return respondJson(HttpStatusCode.BadRequest, json.encodeToString(ErrorResponse.serializer(), ErrorResponse("bad request")))
    try {
        when (request.action) {
            "captcha" -> {
                val captcha = signIn.newCaptcha()
                val reply = CaptchaResponse(signIn.signInNeeded, captcha.nonce, captcha.imageBase64)
                respondJson(HttpStatusCode.OK, json.encodeToString(CaptchaResponse.serializer(), reply))
            }

            "signin" -> {
                val result = signIn.signIn(request.nonce, request.text)
                if (result.ok) onSignedIn()
                respondJson(HttpStatusCode.OK, json.encodeToString(SignInResult.serializer(), result))
            }

            else -> respondJson(HttpStatusCode.BadRequest, json.encodeToString(ErrorResponse.serializer(), ErrorResponse("unknown action")))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.warn("http", "sign-in action failed", "action" to request.action, error = e)
        val message = "سرور به سماد نرسید (${e.javaClass.simpleName}). چند دقیقه‌ی دیگه دوباره امتحان کن."
        respondJson(HttpStatusCode.BadGateway, json.encodeToString(ErrorResponse.serializer(), ErrorResponse(message)))
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private suspend fun ApplicationCall.respondJson(status: HttpStatusCode, text: String) =
    respondText(text, ContentType.Application.Json, status)

private val SIGN_IN_HTML = """
<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>ورود سرور شاینینگ</title>
<style>
  :root { color-scheme: light dark; --bg: #f5f5f2; --fg: #1c1c1a; --muted: #67675f; --card: #ffffff; --line: #dcdcd5; --accent: #1e6b4d; --on-accent: #ffffff; --bad: #b3261e; }
  @media (prefers-color-scheme: dark) { :root { --bg: #141413; --fg: #ececea; --muted: #a2a29b; --card: #1e1e1c; --line: #34342f; --accent: #63c39a; --on-accent: #0d2a1e; --bad: #f2b8b5; } }
  body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.7 system-ui, Tahoma, sans-serif; }
  main { max-width: 420px; margin: 0 auto; padding: 28px 16px; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  #state { margin: 0 0 18px; color: var(--muted); }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
  label { display: block; font-size: 14px; margin: 14px 0 6px; color: var(--muted); }
  input { width: 100%; box-sizing: border-box; font: inherit; font-size: 22px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--bg); color: var(--fg); text-align: center; letter-spacing: 3px; direction: ltr; }
  #token { font-size: 14px; letter-spacing: 0; }
  .captcha { display: flex; gap: 8px; align-items: stretch; }
  .captcha img { flex: 1; min-width: 0; min-height: 64px; border-radius: 8px; background: #fff; object-fit: contain; }
  button { font: inherit; border: 0; border-radius: 8px; padding: 12px 16px; cursor: pointer; }
  button:disabled { opacity: .6; cursor: default; }
  .primary { width: 100%; margin-top: 16px; background: var(--accent); color: var(--on-accent); font-weight: 600; }
  .ghost { background: transparent; color: var(--accent); border: 1px solid var(--line); white-space: nowrap; }
  #msg { margin-top: 12px; min-height: 1.7em; }
  #msg.bad { color: var(--bad); }
  [hidden] { display: none !important; }
</style>
</head>
<body>
<main>
  <h1>ورود سرور به سماد</h1>
  <p id="state">سماد برای ورود کد امنیتی می‌خواد. سرور تصویر رو خودش از سماد می‌گیره؛ تو فقط کدش رو بنویس.</p>
  <div class="card">
    <div id="tokenBox" hidden>
      <label for="token">توکن دیباگ (فقط بار اول؛ توی همین مرورگر می‌مونه)</label>
      <input id="token" autocomplete="off" autocapitalize="off" spellcheck="false">
      <button class="primary" id="saveToken" type="button">ادامه</button>
    </div>
    <form id="form" hidden>
      <div class="captcha">
        <img id="img" alt="کد امنیتی سماد">
        <button type="button" class="ghost" id="again">تصویر تازه</button>
      </div>
      <label for="code">کد توی تصویر</label>
      <input id="code" inputmode="numeric" autocomplete="off" required>
      <button class="primary" type="submit" id="go">ورود</button>
    </form>
    <div id="msg" role="status"></div>
  </div>
</main>
<script>
  const $ = (id) => document.getElementById(id);
  const KEY = 'shiningDebugToken';
  let token = '';
  try { token = localStorage.getItem(KEY) || ''; } catch (e) {}
  let nonce = '';

  function say(text, bad) { $('msg').textContent = text || ''; $('msg').className = bad ? 'bad' : ''; }

  async function call(body) {
    const reply = await fetch('/v1/debug', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Token': token },
      body: JSON.stringify(body),
    });
    if (reply.status === 404) { const e = new Error('token'); e.badToken = true; throw e; }
    if (reply.status === 429) throw new Error('چند ثانیه صبر کن و دوباره امتحان کن.');
    const data = await reply.json().catch(() => ({}));
    if (!reply.ok) throw new Error(data.message || ('خطای ' + reply.status));
    return data;
  }

  function askToken(problem) {
    $('form').hidden = true;
    $('tokenBox').hidden = false;
    say(problem, true);
    $('token').focus();
  }

  function failed(e) { if (e.badToken) askToken('توکن درست نیست.'); else say(e.message, true); }

  async function loadCaptcha() {
    $('img').removeAttribute('src');
    $('code').value = '';
    say('دارم تصویر رو از سماد می‌گیرم…');
    try {
      const d = await call({ action: 'captcha' });
      nonce = d.nonce;
      $('img').src = d.image.startsWith('data:') ? d.image : 'data:image/png;base64,' + d.image;
      $('state').textContent = d.needed
        ? 'سرور الان به سماد وصل نیست و منتظر توئه. کد توی تصویر رو بنویس.'
        : 'سرور الان به سماد وصله. اگه بخوای می‌تونی دوباره واردش کنی.';
      $('tokenBox').hidden = true;
      $('form').hidden = false;
      say('');
      $('code').focus();
    } catch (e) { failed(e); }
  }

  $('saveToken').onclick = () => {
    token = $('token').value.trim();
    try { localStorage.setItem(KEY, token); } catch (e) {}
    loadCaptcha();
  };
  $('again').onclick = loadCaptcha;
  $('form').onsubmit = async (event) => {
    event.preventDefault();
    $('go').disabled = true;
    say('دارم وارد می‌شم…');
    try {
      const d = await call({ action: 'signin', nonce, text: $('code').value });
      if (d.ok) {
        $('form').hidden = true;
        $('state').textContent = d.message + ' تا یک دقیقه‌ی دیگه منوها رو بررسی می‌کنه و نتیجه رو برات می‌فرسته.';
        say('');
      } else {
        await loadCaptcha();
        say(d.message, true);
      }
    } catch (e) { failed(e); }
    $('go').disabled = false;
  };

  if (token) loadCaptcha(); else askToken('');
</script>
</body>
</html>
""".trimIndent()
