import asyncio
import json
import os
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeoutError
from bs4 import BeautifulSoup

categories = [
    'Variables', 'Constants', 'Functions',
    'Keywords', 'Types', 'Operators', 'Annotations'
]

SEM_LIMIT = 5  # Limit simultaneous pages (adjustable)
TIMEOUT_MS = 60000  # Increase timeout to 60 seconds

async def extract_arguments(soup):
    args_header = soup.find('div', string='Arguments')
    if not args_header:
        return []

    args_list = []
    for arg_div in args_header.find_next_siblings('div', class_='tv-pine-reference-item__text'):
        arg_type_span = arg_div.find('span', class_='tv-pine-reference-item__arg-type')
        if not arg_type_span:
            break
        arg_type_text = arg_type_span.text.strip()
        if '(' in arg_type_text:
            arg_name, arg_type = arg_type_text.split('(', 1)
            arg_name = arg_name.strip()
            arg_type = arg_type.replace(')', '').strip()
            args_list.append({"argument": arg_name, "type": arg_type})
    return args_list

async def extract_return_type(soup):
    syntax = soup.find('pre', class_='tv-pine-reference-item__syntax')
    if syntax and '→' in syntax.text:
        return syntax.text.split('→')[-1].strip()
    return ""

async def process_function(context, item, sem):
    async with sem:
        page = await context.new_page()
        try:
            await page.goto(item['url'], timeout=TIMEOUT_MS)
            await page.wait_for_selector('div.tv-pine-reference-item__content', timeout=TIMEOUT_MS)
            content_html = await page.content()
            soup_item = BeautifulSoup(content_html, 'html.parser')
            content_div = soup_item.find('div', class_='tv-pine-reference-item__content')
            if content_div:
                item['info'] = str(content_div)
                item['arguments'] = await extract_arguments(content_div)
                item['returnType'] = await extract_return_type(content_div)
        except PlaywrightTimeoutError:
            print(f"Timeout processing function {item['name']} at {item['url']}")
        finally:
            await page.close()

async def extract_category(browser, version, category, sem):
    context = await browser.new_context()
    page = await context.new_page()
    await page.goto(f'https://www.tradingview.com/pine-script-reference/v{version}/', timeout=TIMEOUT_MS)
    await page.wait_for_selector('div.tv-accordion__section-header', timeout=TIMEOUT_MS)
    soup = BeautifulSoup(await page.content(), 'html.parser')

    headers = soup.find_all('div', class_='tv-accordion__section-header')
    body = next((header.find_next_sibling('div', class_='tv-accordion__section-body') 
                 for header in headers if header.text.strip() == category), None)

    if not body:
        print(f"Body for '{category}' not found in version {version}.")
        await context.close()
        return

    links = body.find_all('a')
    data = [{"name": link.text.strip(),
             "url": f"https://www.tradingview.com/pine-script-reference/v{version}/{link.get('href').strip()}"}
            for link in links]

    if category == 'Functions':
        func_tasks = [process_function(context, item, sem) for item in data]
        await asyncio.gather(*func_tasks)

    os.makedirs(f"./src/main/resources/definitions/v{version}", exist_ok=True)
    filename = f'./src/main/resources/definitions/v{version}/{category.lower()}.json'
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=4)

    print(f"Saved {len(data)} items to {filename} for version {version}.")
    await context.close()

async def process_version(browser, version, sem):
    for cat in categories:
        await extract_category(browser, version, cat, sem)

async def main():
    sem = asyncio.Semaphore(SEM_LIMIT)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        version_tasks = [process_version(browser, v, sem) for v in [3, 4, 5, 6]]
        await asyncio.gather(*version_tasks)
        await browser.close()

asyncio.run(main())