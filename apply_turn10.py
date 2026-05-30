import json
import os

# 1. Read the pristine HEAD index.html
os.system('git restore static/index.html static/styles.css')

with open('static/index.html', 'r', encoding='utf-8') as f:
    html = f.read()

# 2. We need to apply the Turn 10 edit exactly.
# Turn 10 Edit 1: Replaces `openDetail`, `openReason`, and `renderProductDetail`.
# In HEAD, the code to replace starts at `async function openDetail` and ends at the closing brace of `renderProductDetail`.

old_block = """    async function openDetail(productId, dateRecord) {
      const res = await fetch(`/api/products/${state.source}/${productId}?date_record=${encodeURIComponent(dateRecord || '')}`);
      const json = await res.json();
      if (!json.ok) return showToast(json.error || '详情加载失败');
      const item = json.data;
      $('detailBody').innerHTML = renderProductDetail(item);
      $('detailDialog').showModal();
    }
    function openReason(title, text, productTitle = '', grade = '') {
      $('detailBody').innerHTML = title.includes('IP')
        ? formatIpReasonHtml(text, { title: productTitle, ip_grade: grade })
        : `<h4>${escapeHtml(title)}</h4><pre>${escapeHtml(text || '暂无详细原因')}</pre>`;
      $('detailDialog').showModal();
    }
    function renderProductDetail(item) {
      const raw = item.raw_fields || {};
      const period = state.period || '7d';
      const periodLabel = state.periodLabel || '近7天';
      const overview = parseMaybeJson(raw[`overview_${period}`] || (period === '30d' ? raw.sales_overview : null)) || {};
      const distribution = parseMaybeJson(raw[`distribution_${period}`] || raw.distribution_7d || raw.content_ratio) || [];
      const productLink = item.platform_url || (item.source === 'kalodata' ? raw['商品链接'] : buildFastMossUrl(item.product_id));
      const title = item.title || raw.title || raw['商品标题'] || '';
      const category = raw.category || raw['类目'] || raw['商品类目'] || raw.category_name || '';
      const storeName = raw.shop_name || raw.store_name || raw['店铺名称'] || raw['店铺'] || '';
      const metricItems = [
        ['价格', detailValue(raw.real_price, raw.price, raw['价格'])],
        ['评分', detailValue(raw.rating, raw['商品评分'])],
        ['佣金比例', detailValue(raw.commission_rate, raw['佣金比例'])],
        ['总销量', detailValue(raw.sold_count, raw.all_solds, raw['总销量'])],
        [`${periodLabel}销量`, detailValue(overview['销量'], raw.rank_sold_count, raw['销量'])],
        [`${periodLabel}成交额`, detailValue(overview['销售额'], raw.sale_amount, raw['总成交额'])],
        [`${periodLabel}带货达人数`, detailValue(overview['带货达人数'], raw.author_count, raw['关联达人数'])],
        ['上架时间', detailValue(raw.launch_time, raw['上架时间'])],
      ];
      return `<section class="product-detail-page">
        <div class="detail-hero">
          <aside class="detail-gallery">
            <img class="detail-main-image" src="${item.image_url}" alt="${escapeHtml(title)}">
            <a class="detail-primary-link" href="${escapeHtml(productLink || '#')}" target="_blank" rel="noreferrer">${escapeHtml(item.source === 'kalodata' ? '打开 Kalodata' : '打开 FastMoss')}</a>
          </aside>
          <section class="detail-summary">
            <div class="detail-breadcrumb">${escapeHtml(sourceText[item.source] || item.source || '商品')} / 商品详情</div>
            <div class="detail-title-row">
              <h4>${escapeHtml(title)}</h4>
              <div class="detail-actions">${platformLinks({ ...item, platform_url: productLink })}</div>
            </div>
            <div class="detail-tags">
              <span>${escapeHtml(item.product_id)}</span>
              <span>采集 ${escapeHtml(item.date_record || '')}</span>
              ${category ? `<span>${escapeHtml(category)}</span>` : ''}
              ${storeName ? `<span>${escapeHtml(storeName)}</span>` : ''}
              ${gradeBadge(item.ip_grade)}
              ${materialSummaryBadge(item.material_analysis)}
            </div>
            <div class="detail-metric-grid">${metricItems.map(([label, value]) => detailMetricCard(label, value)).join('')}</div>
          </section>
        </div>
        <section class="detail-section">
          <div class="detail-section-head"><h4>数据总览</h4><span>${escapeHtml(periodLabel)} · 按当前采集快照</span></div>
          <div class="detail-overview-grid">
            ${distributionPanel('成交渠道占比', distribution)}
            ${overviewPanel('核心指标', overview)}
          </div>
        </section>
        <section class="detail-section">
          <div class="detail-section-head"><h4>风险与属性分析</h4></div>
          <div class="detail-analysis-grid">
            ${formatIpReasonHtml(item.ip_reason, item)}
            ${materialAnalysisHtml(item.material_analysis)}
          </div>
        </section>
        <details class="detail-raw-fields">
          <summary>查看原始字段</summary>
          <pre>${escapeHtml(JSON.stringify(raw, null, 2))}</pre>
        </details>
      </section>`;
    }"""

new_block = """    let currentDetailItem = null;
    let currentDetailPeriod = '7d';

    async function openDetail(productId, dateRecord) {
      const res = await fetch(`/api/products/${state.source}/${productId}?date_record=${encodeURIComponent(dateRecord || '')}`);
      const json = await res.json();
      if (!json.ok) return showToast(json.error || '详情加载失败');
      currentDetailItem = json.data;
      currentDetailPeriod = state.period === '30d' ? '30d' : '7d';
      renderDetailDialog();
      $('detailDialog').showModal();
    }
    window.setDetailPeriod = function(period) {
      currentDetailPeriod = period;
      renderDetailDialog();
    };
    function renderDetailDialog() {
      if (!currentDetailItem) return;
      $('detailBody').innerHTML = renderProductDetail(currentDetailItem, currentDetailPeriod);
    }
    function openReason(title, text, productTitle = '', grade = '') {
      $('detailBody').innerHTML = title.includes('IP')
        ? formatIpReasonHtml(text, { title: productTitle, ip_grade: grade })
        : `<h4>${escapeHtml(title)}</h4><pre>${escapeHtml(text || '暂无详细原因')}</pre>`;
      $('detailDialog').showModal();
    }
    function renderProductDetail(item, period = '7d') {
      const raw = item.raw_fields || {};
      const periodLabel = period === '30d' ? '近30天' : '近7天';
      const overview = parseMaybeJson(raw[`overview_${period}`] || (period === '30d' ? raw.sales_overview : null)) || {};
      const distribution = parseMaybeJson(raw[`distribution_${period}`] || raw.distribution_7d || raw.content_ratio) || [];
      const productLink = item.platform_url || (item.source === 'kalodata' ? raw['商品链接'] : buildFastMossUrl(item.product_id));
      const title = item.title || raw.title || raw['商品标题'] || '';
      const category = raw.category || raw['类目'] || raw['商品类目'] || raw.category_name || '';
      const storeName = raw.shop_name || raw.store_name || raw['店铺名称'] || raw['店铺'] || '';
      
      const metricItems = [
        ['价格', detailValue(raw.real_price, raw.price, raw['价格'])],
        ['评分', detailValue(raw.rating, raw['商品评分'])],
        ['佣金比例', detailValue(raw.commission_rate, raw['佣金比例'])],
        ['商品热度', detailValue(raw.viral_index)],
        ['人气指数', detailValue(raw.popularity_index)],
        ['总销量', detailValue(raw.sold_count, raw.all_solds, raw['总销量'])],
        ['总GMV', detailValue(raw.sale_amount, raw['总成交额'])],
        ['带货达人数', detailValue(raw.author_count, raw['关联达人数'])],
        ['视频数量', detailValue(raw.aweme_count)],
        ['直播数量', detailValue(raw.live_count)],
        ['上架时间', detailValue(raw.launch_time, raw['上架时间'])],
      ].filter(([, v]) => v !== undefined && v !== '');

      return `<section class="product-detail-page">
        <div class="detail-hero">
          <aside class="detail-gallery">
            <img class="detail-main-image" src="${item.image_url}" alt="${escapeHtml(title)}">
            <a class="detail-primary-link" href="${escapeHtml(productLink || '#')}" target="_blank" rel="noreferrer">${escapeHtml(item.source === 'kalodata' ? '打开 Kalodata' : '打开 FastMoss')}</a>
          </aside>
          <section class="detail-summary">
            <div class="detail-breadcrumb">${escapeHtml(sourceText[item.source] || item.source || '商品')} / 商品详情</div>
            <div class="detail-title-row">
              <h4>${escapeHtml(title)}</h4>
              <div class="detail-actions">${platformLinks({ ...item, platform_url: productLink })}</div>
            </div>
            <div class="detail-tags">
              <span>${escapeHtml(item.product_id)}</span>
              <span>采集 ${escapeHtml(item.date_record || '')}</span>
              ${category ? `<span>${escapeHtml(category)}</span>` : ''}
              ${storeName ? `<span>${escapeHtml(storeName)}</span>` : ''}
              ${gradeBadge(item.ip_grade)}
              ${materialSummaryBadge(item.material_analysis)}
            </div>
            <div class="detail-metric-grid">${metricItems.map(([label, value]) => detailMetricCard(label, value)).join('')}</div>
          </section>
        </div>
        <section class="detail-section">
          <div class="detail-section-head">
            <div style="display:flex; align-items:center; gap:16px;">
              <h4>数据总览</h4>
              <div class="detail-period-switch">
                <button type="button" class="${period === '7d' ? 'active' : ''}" onclick="setDetailPeriod('7d')">近7天</button>
                <button type="button" class="${period === '30d' ? 'active' : ''}" onclick="setDetailPeriod('30d')">近30天</button>
              </div>
            </div>
            <span>按当前采集快照</span>
          </div>
          <div class="detail-overview-grid">
            ${distributionPanel('成交渠道占比', distribution)}
            ${overviewPanel('核心指标', overview)}
          </div>
        </section>
        <section class="detail-section">
          <div class="detail-section-head"><h4>风险与属性分析</h4></div>
          <div class="detail-analysis-grid">
            ${formatIpReasonHtml(item.ip_reason, item)}
            ${materialAnalysisHtml(item.material_analysis)}
          </div>
        </section>
        <details class="detail-raw-fields">
          <summary>查看原始字段</summary>
          <pre>${escapeHtml(JSON.stringify(raw, null, 2))}</pre>
        </details>
      </section>`;
    }"""

if old_block in html:
    html = html.replace(old_block, new_block)
else:
    print("WARNING: Could not find block 1 to replace.")

with open('static/index.html', 'w', encoding='utf-8') as f:
    f.write(html)

# 3. Add CSS for detail-period-switch
with open('static/styles.css', 'a', encoding='utf-8') as f:
    f.write('''

.detail-period-switch {
  display: flex;
  background: #f1f5f9;
  border-radius: 6px;
  padding: 2px;
}

.detail-period-switch button {
  border: none;
  background: transparent;
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 4px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.detail-period-switch button:hover {
  color: #1e293b;
}

.detail-period-switch button.active {
  background: white;
  color: #0f172a;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  font-weight: 500;
}
''')
