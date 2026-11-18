DELETE FROM user_ads
WHERE ad_id IN (
    SELECT id
    FROM ads
    WHERE url = 'https://civitelladelago.com'
);

DELETE FROM ads
WHERE url = 'https://civitelladelago.com';
