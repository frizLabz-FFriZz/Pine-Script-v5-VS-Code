from playwright.sync_api import sync_playwright
from bs4 import BeautifulSoup
import json

base_url = 'https://www.tradingview.com/pine-script-reference/v6/'

categories = [
    'Variables', 'Constants', 'Functions', 'Keywords', 'Types', 'Operators', 'Annotations'
]

def extract_arguments(content):
    soup = BeautifulSoup(content, 'html.parser')
    args_header = soup.find('div', string='Arguments')
    if not args_header:
        return []

    args_list = []
    for arg_div in args_header.find_next_siblings('div', class_='tv-pine-reference-item__text'):
        arg_type_span = arg_div.find('span', class_='tv-pine-reference-item__arg-type')
        if not arg_type_span:
            break
        arg_type_text = arg_type_span.text.strip()
        arg_name, arg_type = arg_type_text.split('(', 1)
        arg_name = arg_name.strip()
        arg_type = arg_type.replace(')', '').strip()
        args_list.append({"argument": arg_name, "type": arg_type})

    return args_list

def extract_category(page, category):
    page.wait_for_selector('div.tv-accordion__section-header')
    content = page.content()
    soup = BeautifulSoup(content, 'html.parser')
    headers = soup.find_all('div', class_='tv-accordion__section-header')

    body = None
    for header in headers:
        if header.text.strip() == category:
            body = header.find_next_sibling('div', class_='tv-accordion__section-body')
            break

    if not body:
        print(f"Body for '{category}' not found.")
        return

    links = body.find_all('a')
    data = []

    for link in links:
        name = link.text.strip()
        href = link.get('href').strip()
        full_url = base_url + href

        item = {"name": name, "url": full_url}

        if category == 'Functions':
            page.goto(full_url)
            page.wait_for_selector('div.tv-pine-reference-item__content')
            item['arguments'] = extract_arguments(page.content())

        data.append(item)

    filename = f'{category.lower()}.json'
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=4)

    print(f"Saved {len(data)} items to {filename}.")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto(base_url)

    for cat in categories:
        extract_category(page, cat)

    browser.close()
