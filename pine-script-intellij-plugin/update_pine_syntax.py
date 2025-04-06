import asyncio
import json
import os
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeoutError
from bs4 import BeautifulSoup

# Categories to scrape
categories = [
    'Variables', 'Constants', 'Functions',
    'Keywords', 'Types', 'Operators', 'Annotations'
]

# Concurrency limit and page load timeout
SEM_LIMIT = 10       # Limit how many pages open simultaneously
TIMEOUT_MS = 60000   # 60 seconds page load timeout

def extract_function_details(html: str, fragment: str) -> dict:
    """
    Extracts function details from the dynamically loaded HTML block with id equal to the URL fragment.
    """
    details = {
        "info": "",
        "description": "",
        "arguments": [],
        "syntax": "",
        "returnType": "",
        "returns": ""
    }
    soup = BeautifulSoup(html, 'html.parser')
    info_div = soup.find('div', id=fragment)
    if not info_div:
        return details

    details["info"] = str(info_div)

    # Extract description: first <div> with class 'tv-pine-reference-item__text tv-text'
    desc_div = info_div.find('div', class_='tv-pine-reference-item__text tv-text')
    if desc_div:
        details["description"] = desc_div.decode_contents().strip()
    else:
        # Fallback: find the div immediately preceding a Syntax header
        syntax_header = info_div.find('div', string=lambda text: text and text.startswith('Syntax'))
        if syntax_header:
            prev_div = syntax_header.find_previous_sibling('div', class_='tv-pine-reference-item__text tv-text')
            if prev_div:
                details["description"] = prev_div.decode_contents().strip()

    # Extract syntax: try for <pre> with class 'tv-pine-reference-item__syntax with-overloads selected' first
    syntax_div = info_div.find('pre', class_='tv-pine-reference-item__syntax with-overloads selected')
    if not syntax_div:
        syntax_div = info_div.find('pre', class_='tv-pine-reference-item__syntax selected')
    if syntax_div:
        syntax_text = syntax_div.get_text(strip=True)
        details["syntax"] = syntax_text
        if '→' in syntax_text:
            details["returnType"] = syntax_text.split('→')[-1].strip()

    # Extract arguments: find all <span> elements with class 'tv-pine-reference-item__arg-type'
    arg_spans = info_div.find_all('span', class_='tv-pine-reference-item__arg-type')
    for span in arg_spans:
        text = span.get_text(strip=True)
        if '(' in text:
            arg_name, arg_type = text.split('(', 1)
            arg_name = arg_name.strip()
            arg_type = arg_type.replace(')', '').strip()
        else:
            arg_name = text
            arg_type = ""
        details["arguments"].append({"argument": arg_name, "type": arg_type})

    # Extract returns: find the <div> with text 'Returns' and then its next sibling with class 'tv-pine-reference-item__text tv-text'
    returns_header = info_div.find('div', string='Returns')
    if returns_header:
        returns_div = returns_header.find_next_sibling('div', class_='tv-pine-reference-item__text tv-text')
        if returns_div:
            details["returns"] = returns_div.get_text(strip=True)

    return details

def extract_variable_details(html: str, fragment: str) -> dict:
    """
    Extracts variable details from the dynamically loaded HTML block with id equal to the URL fragment.
    """
    details = {
        "info": "",
        "description": "",
        "type": "",
        "remarks": ""
    }
    soup = BeautifulSoup(html, 'html.parser')
    info_div = soup.find('div', id=fragment)
    if not info_div:
        return details

    details["info"] = str(info_div)

    # Extract description: first <div> with class 'tv-pine-reference-item__text tv-text'
    desc_div = info_div.find('div', class_='tv-pine-reference-item__text tv-text')
    if desc_div:
        details["description"] = desc_div.decode_contents().strip()
    else:
        # Fallback: find the div immediately preceding a 'Type' header
        type_header = info_div.find('div', string=lambda text: text and text.startswith('Type'))
        if type_header:
            prev_div = type_header.find_previous_sibling('div', class_='tv-pine-reference-item__text tv-text')
            if prev_div:
                details["description"] = prev_div.decode_contents().strip()

    # Extract type: find the <div> with text 'Type' and then its next sibling with class 'tv-pine-reference-item__text tv-text'
    type_header = info_div.find('div', string='Type')
    if type_header:
        type_div = type_header.find_next_sibling('div', class_='tv-pine-reference-item__text tv-text')
        if type_div:
            details["type"] = type_div.get_text(strip=True)

    # Extract remarks: collect all <div class="tv-pine-reference-item__text tv-text"> after the 'Remarks' header until the next sub-header
    remarks_header = info_div.find('div', string='Remarks')
    if remarks_header:
        remarks_list = []
        for sibling in remarks_header.find_next_siblings():
            # Stop if a new sub-header is encountered
            if sibling.has_attr('class') and 'tv-pine-reference-item__sub-header' in sibling.get('class', []):
                break
            if sibling.name == 'div' and sibling.has_attr('class'):
                classes = sibling.get('class')
                if 'tv-pine-reference-item__text' in classes and 'tv-text' in classes:
                    remarks_list.append(sibling.decode_contents().strip())
        details["remarks"] = remarks_list

    return details

async def process_function(context, item, sem):
    async with sem:
        page = await context.new_page()
        try:
            # Use the fragment provided in the item, or fallback to URL split
            fragment = item.get('fragment', item['url'].split('#')[-1])
            await page.goto(item['url'], timeout=TIMEOUT_MS)
            # Wait for the dynamically loaded <div> with the matching id, escaping dots using attribute selector
            await page.wait_for_selector(f'div[id="{fragment}"]', timeout=TIMEOUT_MS)

            content_html = await page.content()
            # Parse the function details from the specific <div> identified by the fragment
            details = extract_function_details(content_html, fragment)

            # Update item fields
            item["info"] = details["info"]
            item["description"] = details["description"]
            item["arguments"] = details["arguments"]
            item["syntax"] = details["syntax"]
            item["returnType"] = details["returnType"]
            item["returns"] = details["returns"]

        except PlaywrightTimeoutError:
            print(f"Timeout processing function {item['name']} at {item['url']}")
        finally:
            await page.close()

async def process_variable(context, item, sem):
    async with sem:
        page = await context.new_page()
        try:
            # Use the fragment provided in the item, or fallback to URL split
            fragment = item.get('fragment', item['url'].split('#')[-1])
            await page.goto(item['url'], timeout=TIMEOUT_MS)
            # Wait for the dynamically loaded <div> with the matching id, escaping dots using attribute selector
            await page.wait_for_selector(f'div[id="{fragment}"]', timeout=TIMEOUT_MS)

            content_html = await page.content()
            # Parse the variable details from the specific <div> identified by the fragment
            details = extract_variable_details(content_html, fragment)

            # Update item fields
            item["info"] = details["info"]
            item["description"] = details["description"]
            item["type"] = details["type"]
            item["remarks"] = details["remarks"]

        except PlaywrightTimeoutError:
            print(f"Timeout processing variable {item['name']} at {item['url']}")
        finally:
            await page.close()

async def extract_category(browser, version, category, sem):
    context = await browser.new_context()
    page = await context.new_page()

    # Visit the main reference index page for the given version
    await page.goto(f'https://www.tradingview.com/pine-script-reference/v{version}/', timeout=TIMEOUT_MS)
    await page.wait_for_selector('div.tv-accordion__section-header', timeout=TIMEOUT_MS)

    # Parse the loaded HTML
    soup = BeautifulSoup(await page.content(), 'html.parser')
    headers = soup.find_all('div', class_='tv-accordion__section-header')

    # Locate the body that corresponds to this category
    body = next(
        (
            header.find_next_sibling('div', class_='tv-accordion__section-body')
            for header in headers
            if header.text.strip() == category
        ),
        None
    )

    if not body:
        print(f"Body for '{category}' not found in version {version}.")
        await context.close()
        return

    # Gather data from each <a> link in that section
    links = body.find_all('a')
    if category in ['Functions', 'Variables']:
        data = [
            {
                "name": link.text.strip(),
                "url": f"https://www.tradingview.com/pine-script-reference/v{version}/{link.get('href').strip()}",
                "fragment": link.get('href').strip().lstrip('#')
            }
            for link in links
        ]
    else:
        data = [
            {
                "name": link.text.strip(),
                "url": f"https://www.tradingview.com/pine-script-reference/v{version}/{link.get('href').strip()}"
            }
            for link in links
        ]

    if category in ['Functions', 'Variables']:
        data = data[:5]  # For testing: limit to first 5 items; remove or adjust as needed
        tasks = []
        for item in data:
            if category == 'Functions':
                tasks.append(process_function(context, item, sem))
            else:  # category == 'Variables'
                tasks.append(process_variable(context, item, sem))
        await asyncio.gather(*tasks)

    # Save the results to JSON
    out_dir = f"./src/main/resources/definitions/v{version}"
    os.makedirs(out_dir, exist_ok=True)
    filename = os.path.join(out_dir, f"{category.lower()}.json")

    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=4)

    print(f"Saved {len(data)} items to {filename} for version {version}.")
    await context.close()

async def process_version(browser, version, sem):
    # Iterate through each category for the given version
    for cat in categories:
        await extract_category(browser, version, cat, sem)

async def main():
    sem = asyncio.Semaphore(SEM_LIMIT)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        version_tasks = [process_version(browser, v, sem) for v in [3, 4, 5, 6]]
        await asyncio.gather(*version_tasks)
        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())