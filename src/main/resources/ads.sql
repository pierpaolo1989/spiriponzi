INSERT INTO ads(title, reward, timer, url, active)
SELECT 'P4P - The best community about amateur photographers', 20, 10, 'https://www.instagram.com/_passion4pics/#', true
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE url = 'https://www.instagram.com/_passion4pics/#');

INSERT INTO ads(title, reward, timer, url, active)
SELECT 'Pierpaolo Di Dato''s blog - Articles about coding and finance', 20, 10, 'https://blog.pierpaolodidato.it', true
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE url = 'https://blog.pierpaolodidato.it');

INSERT INTO ads(title, reward, timer, url, active)
SELECT 'microeconomia.info - microeconomics exercises with solutions', 20, 10, 'https://microeconomia.info', true
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE url = 'https://microeconomia.info');

INSERT INTO ads(title, reward, timer, url, active)
SELECT 'Unofficial website about Civitella del Lago - historical village near Orvieto (Italy)', 20, 10, 'https://civitelladelago.com', true
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE url = 'https://civitelladelago.com');

INSERT INTO ads(title, reward, timer, url, active)
SELECT 'Urban Scratches: Journey through street art of Naples and province', 20, 10, 'https://www.amazon.it/Urban-Scratches-Journey-through-province-ebook/dp/B09FC5L51Q/ref=sr_1_2?__mk_it_IT=%C3%85M%C3%85%C5%BD%C3%95%C3%91&sr=8-2', true
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE url = 'https://www.amazon.it/Urban-Scratches-Journey-through-province-ebook/dp/B09FC5L51Q/ref=sr_1_2?__mk_it_IT=%C3%85M%C3%85%C5%BD%C3%95%C3%91&sr=8-2');
