const json = (data, status = 200, origin = "*") =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=UTF-8",
      "access-control-allow-origin": origin,
      "access-control-allow-methods": "GET, POST, OPTIONS",
      "access-control-allow-headers": "content-type",
    },
  });

const escapePathSegment = (segment) => encodeURIComponent(segment).replace(/%2F/g, "/");

const normalizeTargetPath = (requestedPath, fileName) => {
  const cleanFileName = fileName.split("/").pop().split("\\").pop();
  const rawPath = (requestedPath || "").trim().replaceAll("\\", "/");
  const cleanPath = rawPath.startsWith("/") ? rawPath.slice(1) : rawPath;

  if (!cleanFileName) {
    throw new Error("缺少檔名");
  }
  if (cleanPath.includes("..")) {
    throw new Error("上傳路徑不能包含 ..");
  }
  if (!cleanPath) {
    return `uploads/${cleanFileName}`;
  }
  if (cleanPath.endsWith("/")) {
    return `${cleanPath}${cleanFileName}`;
  }
  return cleanPath;
};

const githubHeaders = (env) => ({
  Accept: "application/vnd.github+json",
  Authorization: `Bearer ${env.GITHUB_TOKEN}`,
  "X-GitHub-Api-Version": "2022-11-28",
});

const contentsUrl = (env, targetPath) => {
  const encodedPath = targetPath
    .split("/")
    .map((segment) => escapePathSegment(segment))
    .join("/");

  return `https://api.github.com/repos/${escapePathSegment(env.GITHUB_OWNER)}/${escapePathSegment(env.GITHUB_REPO)}/contents/${encodedPath}`;
};

const htmlUrl = (env, targetPath) =>
  `https://github.com/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/blob/${env.GITHUB_BRANCH}/${targetPath}`;

const toBase64 = (arrayBuffer) => {
  const bytes = new Uint8Array(arrayBuffer);
  const chunkSize = 0x8000;
  let binary = "";

  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...chunk);
  }

  return btoa(binary);
};

const loadExistingSha = async (env, targetPath) => {
  const response = await fetch(contentsUrl(env, targetPath), {
    method: "GET",
    headers: githubHeaders(env),
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`查詢 GitHub 檔案失敗，HTTP ${response.status}`);
  }

  const payload = await response.json();
  return payload.sha || null;
};

const uploadToGitHub = async (env, targetPath, fileName, content, sha) => {
  const response = await fetch(contentsUrl(env, targetPath), {
    method: "PUT",
    headers: {
      ...githubHeaders(env),
      "content-type": "application/json; charset=UTF-8",
    },
    body: JSON.stringify({
      message: `Upload ${fileName} from GitHub Pages`,
      content,
      branch: env.GITHUB_BRANCH,
      ...(sha ? { sha } : {}),
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub API 回傳 HTTP ${response.status}：${body}`);
  }

  return htmlUrl(env, targetPath);
};

export default {
  async fetch(request, env) {
    const origin = request.headers.get("origin") || "*";

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": origin,
          "access-control-allow-methods": "GET, POST, OPTIONS",
          "access-control-allow-headers": "content-type",
        },
      });
    }

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, message: "worker-ready" }, 200, origin);
    }

    if (request.method !== "POST" || url.pathname !== "/upload") {
      return json({ ok: false, message: "找不到這個 API 路徑" }, 404, origin);
    }

    if (!env.GITHUB_TOKEN) {
      return json({ ok: false, message: "Worker 尚未設定 GITHUB_TOKEN secret" }, 500, origin);
    }

    try {
      const form = await request.formData();
      const file = form.get("file");
      const requestedPath = form.get("targetPath");

      if (!(file instanceof File) || file.size === 0) {
        return json({ ok: false, message: "請先選擇要上傳的檔案" }, 400, origin);
      }

      const targetPath = normalizeTargetPath(
        typeof requestedPath === "string" ? requestedPath : "",
        file.name
      );
      const fileBytes = await file.arrayBuffer();
      const base64Content = toBase64(fileBytes);
      const sha = await loadExistingSha(env, targetPath);
      const url = await uploadToGitHub(env, targetPath, file.name, base64Content, sha);

      return json(
        {
          ok: true,
          message: "檔案已成功上傳到 GitHub",
          path: targetPath,
          url,
        },
        200,
        origin
      );
    } catch (error) {
      return json(
        {
          ok: false,
          message: error instanceof Error ? error.message : "上傳失敗",
        },
        500,
        origin
      );
    }
  },
};
