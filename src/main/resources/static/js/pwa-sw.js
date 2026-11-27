/* Offline support for the public pages. Loaded by /sw.js through importScripts(). */
const PWA_CACHE_VERSION = 'spiriponzi-v1';
const PWA_STATIC_CACHE = `${PWA_CACHE_VERSION}-static`;
const PWA_PAGE_CACHE = `${PWA_CACHE_VERSION}-pages`;
const PWA_OFFLINE_URL = '/offline.html';
const PWA_PUBLIC_PAGES = ['/', '/index', '/faq', '/terms', '/login', '/register', '/recover-password'];
const PWA_STATIC_PREFIXES = ['/css/', '/js/', '/img/', '/vendor/'];
const PWA_PRECACHE = [
	PWA_OFFLINE_URL,
	'/manifest.webmanifest',
	'/css/sb-admin-2.css',
	'/css/public-pages.css',
	'/js/public-navbar.js',
	'/img/spiriponzi-mark.svg',
	'/img/favicon.ico'
];

self.addEventListener('install', (event) => {
	event.waitUntil(
		caches.open(PWA_STATIC_CACHE)
			.then((cache) => cache.addAll(PWA_PRECACHE))
			.then(() => self.skipWaiting())
	);
});

self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches.keys()
			.then((keys) => Promise.all(keys
				.filter((key) => key.startsWith('spiriponzi-') && ![PWA_STATIC_CACHE, PWA_PAGE_CACHE].includes(key))
				.map((key) => caches.delete(key))))
			.then(() => self.clients.claim())
	);
});

self.addEventListener('fetch', (event) => {
	const request = event.request;
	const url = new URL(request.url);

	if (request.method !== 'GET' || url.origin !== self.location.origin) {
		return;
	}

	if (request.mode === 'navigate') {
		event.respondWith(handleNavigation(request, url));
		return;
	}

	if (PWA_STATIC_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) {
		event.respondWith(staleWhileRevalidate(request));
	}
});

async function handleNavigation(request, url) {
	try {
		const response = await fetch(request);
		// Only anonymous, successful public pages are cached; account pages never are.
		if (response.ok && !response.redirected && !url.search && PWA_PUBLIC_PAGES.includes(url.pathname)) {
			const cache = await caches.open(PWA_PAGE_CACHE);
			cache.put(request, response.clone());
		}
		return response;
	} catch (error) {
		const cached = await caches.match(request, { ignoreSearch: true });
		return cached || caches.match(PWA_OFFLINE_URL);
	}
}

async function staleWhileRevalidate(request) {
	const cache = await caches.open(PWA_STATIC_CACHE);
	const cached = await cache.match(request);
	const network = fetch(request)
		.then((response) => {
			if (response.ok) {
				cache.put(request, response.clone());
			}
			return response;
		})
		.catch(() => cached);
	return cached || network;
}
