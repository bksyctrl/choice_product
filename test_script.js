
    const state = { source: 'unified', view: 'table', page: 1, pageSize: 30, total: 0, items: [], periodLabel: '近7天', sortBy: 'date_record', sortOrder: 'desc' };
    const statusText = { PENDING: '待选中', REVIEWING: '待复核', READY: '待上架', PUBLISHED: '已上架', OTHER: '其他' };
    const sourceText = { unified: '统一表', fastmoss: 'fastmoss', kalodata: 'kalodata' };
    const $ = (id) => document.getElementById(id);
    const filterDefinitions = [
      { key: 'dateRecord', label: '采集日期', type: 'dateRange', startId: 'dateStart', endId: 'dateEnd', startLabel: '采集开始', endLabel: '采集结束' },
      { key: 'launchTime', label: '上架时间', type: 'dateRange', startId: 'launchStart', endId: 'launchEnd', startLabel: '上架开始', endLabel: '上架结束' },
      { key: 'commission', label: '佣金比例', type: 'range', minId: 'commissionMin', maxId: 'commissionMax', suffix: '%', placeholderMin: '最低佣金', placeholderMax: '最高佣金' },
      { key: 'author', label: '达人数量', type: 'range', minId: 'authorMin', maxId: 'authorMax', placeholderMin: '最少达人', placeholderMax: '最多达人' },
      { key: 'productCardRatio', label: '商品卡比例', type: 'range', minId: 'productCardRatioMin', maxId: 'productCardRatioMax', suffix: '%', placeholderMin: '最低比例', placeholderMax: '最高比例' },
      { key: 'transportFee', label: '运费', type: 'range', minId: 'transportFeeMin', maxId: 'transportFeeMax', placeholderMin: '最低运费', placeholderMax: '最高运费' },
      { key: 'sold', label: '总销量', type: 'range', minId: 'soldMin', maxId: 'soldMax', placeholderMin: '最低销量', placeholderMax: '最高销量' },
      { key: 'videoRatio', label: '视频成交占比', type: 'range', minId: 'videoRatioMin', maxId: 'videoRatioMax', suffix: '%', placeholderMin: '最低占比', placeholderMax: '最高占比' },
      { key: 'rating', label: '评分', type: 'range', minId: 'ratingMin', maxId: 'ratingMax', placeholderMin: '最低评分', placeholderMax: '最高评分' },
      { key: 'price', label: '售价范围', type: 'range', minId: 'priceMin', maxId: 'priceMax', placeholderMin: '最低售价', placeholderMax: '最高售价' },
      { key: 'basePrice', label: '定价范围', type: 'range', minId: 'basePriceMin', maxId: 'basePriceMax', placeholderMin: '最低定价', placeholderMax: '最高定价' },
      {
        key: 'auditStatus',
        label: '商品状态',
        type: 'select',
        id: 'auditStatus',
        options: [
          ['', '全部'],
          ['PENDING', '待选中'],
          ['REVIEWING', '待复核'],
          ['READY', '待上架'],
          ['PUBLISHED', '已上架'],
          ['OTHER', '其他'],
        ],
      },
    ];
    const filterByKey = Object.fromEntries(filterDefinitions.map(item => [item.key, item]));
    const activeFilterKeys = new Set();
    const filterValues = {};
    const paramMap = {
      keyword: 'keyword', dateStart: 'date_start', dateEnd: 'date_end',
      launchStart: 'launch_start', launchEnd: 'launch_end', auditStatus: 'audit_status',
      ipGradeMin: 'ip_grade_min', ipGradeMax: 'ip_grade_max', materialType: 'material_type',
      materialCategories: 'material_categories', excludeMaterialCategories: 'exclude_material_categories',
      soldMin: 'sold_min', soldMax: 'sold_max', saleAmountMin: 'sale_amount_min', saleAmountMax: 'sale_amount_max',
      priceMin: 'price_min', priceMax: 'price_max', commissionMin: 'commission_min', commissionMax: 'commission_max',
      authorMin: 'author_min', authorMax: 'author_max', ratingMin: 'rating_min', ratingMax: 'rating_max',
      basePriceMin: 'base_price_min', basePriceMax: 'base_price_max',
      transportFeeMin: 'transport_fee_min', transportFeeMax: 'transport_fee_max',
      productCardRatioMin: 'product_card_ratio_min', productCardRatioMax: 'product_card_ratio_max',
      videoRatioMin: 'video_ratio_min', videoRatioMax: 'video_ratio_max',
      salesStart: 'sales_start', salesEnd: 'sales_end'
    };

    document.querySelectorAll('#sourceNav button').forEach(btn => btn.addEventListener('click', () => {
      state.source = btn.dataset.source;
      state.page = 1;
      document.querySelectorAll('#sourceNav button').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      $('pageTitle').textContent = sourceText[state.source];
      $('exportReadyLink').href = `/api/products/${state.source}/export-ready-links`;
      loadDefaultDateAndData();
    }));
    document.querySelectorAll('.view-switch button').forEach(btn => btn.addEventListener('click', () => {
      state.view = btn.dataset.view;
      document.querySelectorAll('.view-switch button').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      renderItems();
    }));
    $('sidebarSearchBtn').addEventListener('click', () => { state.page = 1; loadAll(); });
    $('topSearchBtn').addEventListener('click', () => { state.page = 1; loadAll(); });
    $('sortBy').addEventListener('change', () => { state.sortBy = $('sortBy').value; state.page = 1; loadProducts(); });
    $('resetFiltersBtn').addEventListener('click', resetFilters);
    initSalesRangeFilter();
    $('pageSize').addEventListener('change', () => { state.pageSize = Number($('pageSize').value); state.page = 1; loadProducts(); });
    $('prevPage').addEventListener('click', () => { if (state.page > 1) { state.page -= 1; loadProducts(); } });
    $('nextPage').addEventListener('click', () => { if (state.page * state.pageSize < state.total) { state.page += 1; loadProducts(); } });
    $('copyReadyBtn').addEventListener('click', copyReadyLinks);
    $('closeDetail').addEventListener('click', () => $('detailDialog').close());
    $('closeImage').addEventListener('click', () => $('imageDialog').close());
    initCustomFilters();

    function initCustomFilters() {
      $('addFilterBtn').addEventListener('click', () => {
        renderFilterOptions();
        $('filterDialog').showModal();
      });
      $('closeFilterDialog').addEventListener('click', () => $('filterDialog').close());
      renderActiveFilters();
    }

    function renderFilterOptions() {
      $('filterOptions').innerHTML = filterDefinitions.map(def => `
        <button type="button" data-filter-key="${def.key}" ${activeFilterKeys.has(def.key) ? 'disabled' : ''}>
          <span>${def.label}</span>
          <small>${activeFilterKeys.has(def.key) ? '已添加' : filterOptionHint(def)}</small>
        </button>
      `).join('');
      document.querySelectorAll('[data-filter-key]').forEach(btn => btn.addEventListener('click', () => {
        addFilter(btn.dataset.filterKey);
        $('filterDialog').close();
        renderFilterOptions();
      }));
    }

    function filterOptionHint(def) {
      if (def.type === 'dateRange') return '输入开始时间与结束时间';
      if (def.type === 'select') return '选择状态';
      return '输入最小值与最大值';
    }

    function addFilter(key) {
      activeFilterKeys.add(key);
      renderActiveFilters();
    }

    function removeFilter(key) {
      const def = filterByKey[key];
      activeFilterKeys.delete(key);
      filterInputIds(def).forEach(id => delete filterValues[id]);
      renderActiveFilters();
    }

    function renderActiveFilters() {
      if (!activeFilterKeys.size) {
        $('activeFilters').innerHTML = '<div class="empty-filter">点击“添加筛选条件”开始组合筛选</div>';
        return;
      }
      $('activeFilters').innerHTML = Array.from(activeFilterKeys).map(key => filterCardTemplate(filterByKey[key])).join('');
      document.querySelectorAll('[data-remove-filter]').forEach(btn => btn.addEventListener('click', () => removeFilter(btn.dataset.removeFilter)));
      document.querySelectorAll('[data-date-preset]').forEach(btn => btn.addEventListener('click', () => applyDatePreset(btn.dataset.datePreset)));
      document.querySelectorAll('[data-filter-input]').forEach(input => {
        const rememberValue = () => { filterValues[input.id] = input.value; };
        input.addEventListener('input', rememberValue);
        input.addEventListener('change', rememberValue);
      });
    }

    function filterCardTemplate(def) {
      return `<div class="filter-card">
        <div class="filter-card-head"><strong>${def.label}</strong><button type="button" data-remove-filter="${def.key}">移除</button></div>
        ${filterControlTemplate(def)}
      </div>`;
    }

    function filterControlTemplate(def) {
      if (def.type === 'dateRange') {
        if (def.key === 'dateRecord') {
          return `<div class="filter-control date-preset-control">
            <div class="date-preset-buttons">
              <button type="button" data-date-preset="yesterday">昨日</button>
              <button type="button" data-date-preset="7d">过去7天</button>
              <button type="button" data-date-preset="30d">过去30天</button>
              <button type="button" data-date-preset="custom">自定义</button>
            </div>
            <div class="filter-control range-row date-input-row">
              <input data-filter-input id="${def.startId}" type="date" value="${escapeHtml(filterValues[def.startId] || '')}">
              <span>→</span>
              <input data-filter-input id="${def.endId}" type="date" value="${escapeHtml(filterValues[def.endId] || '')}">
            </div>
          </div>`;
        }
        return `<div class="filter-control two-cols">
          <label>${def.startLabel}<input data-filter-input id="${def.startId}" type="date" value="${escapeHtml(filterValues[def.startId] || '')}"></label>
          <label>${def.endLabel}<input data-filter-input id="${def.endId}" type="date" value="${escapeHtml(filterValues[def.endId] || '')}"></label>
        </div>`;
      }
      if (def.type === 'select') {
        return `<label class="filter-control">${def.label}
          <select data-filter-input id="${def.id}">
            ${def.options.map(([value, label]) => `<option value="${value}" ${(filterValues[def.id] || '') === value ? 'selected' : ''}>${label}</option>`).join('')}
          </select>
        </label>`;
      }
      return `<div class="filter-control range-row">
        <input data-filter-input id="${def.minId}" inputmode="decimal" placeholder="${def.placeholderMin || '最小值'}${def.suffix || ''}" value="${escapeHtml(filterValues[def.minId] || '')}">
        <span>-</span>
        <input data-filter-input id="${def.maxId}" inputmode="decimal" placeholder="${def.placeholderMax || '最大值'}${def.suffix || ''}" value="${escapeHtml(filterValues[def.maxId] || '')}">
      </div>`;
    }

    function filterInputIds(def) {
      if (!def) return [];
      if (def.type === 'dateRange') return [def.startId, def.endId];
      if (def.type === 'select') return [def.id];
      return [def.minId, def.maxId];
    }

    async function applyDatePreset(preset) {
      if (preset === 'custom') return;
      const latest = await getLatestCollectionDate();
      const days = preset === 'yesterday' ? 1 : Number(preset.replace('d', '')) || 7;
      setFilterValue('dateStart', formatDate(shiftDate(latest, -(days - 1))));
      setFilterValue('dateEnd', formatDate(latest));
    }

    async function getLatestCollectionDate() {
      try {
        const res = await fetch(`/api/products/latest-date?source=${state.source}`);
        const json = await res.json();
        if (json.ok && json.data.latest_date) return new Date(json.data.latest_date.slice(0, 10));
      } catch (error) {
        console.warn('latest date load failed', error);
      }
      return shiftDate(new Date(), -1);
    }

    function shiftDate(date, days) {
      const copy = new Date(date);
      copy.setDate(copy.getDate() + days);
      return copy;
    }
    function formatDate(date) {
      const y = date.getFullYear();
      const m = String(date.getMonth() + 1).padStart(2, '0');
      const d = String(date.getDate()).padStart(2, '0');
      return `${y}-${m}-${d}`;
    }
    function formatShortDate(date) {
      return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`;
    }

    function initSalesRangeFilter() {
      const box = $('salesRangeFilter');
      const trigger = $('salesRangeTrigger');
      trigger.addEventListener('click', () => box.classList.toggle('open'));
      document.querySelectorAll('[data-sales-range]').forEach(btn => btn.addEventListener('click', () => {
        const range = btn.dataset.salesRange;
        if (range === 'custom') {
          $('period').value = 'custom';
          $('salesCustomRange').classList.add('visible');
          trigger.textContent = formatSalesRangeLabel();
          return;
        }
        $('salesCustomRange').classList.remove('visible');
        setSalesRangePreset(range);
        box.classList.remove('open');
        state.page = 1;
        loadProducts();
      }));
      $('applySalesRange').addEventListener('click', () => {
        if (!$('salesStart').value || !$('salesEnd').value) return showToast('请选择销售开始和结束日期');
        $('period').value = 'custom';
        trigger.textContent = formatSalesRangeLabel();
        box.classList.remove('open');
        state.page = 1;
        loadProducts();
      });
      document.addEventListener('click', (event) => {
        if (!box.contains(event.target)) box.classList.remove('open');
      });
    }
    function setSalesRangePreset(range) {
      const today = new Date();
      const yesterday = shiftDate(today, -1);
      if (range === 'yesterday') {
        $('period').value = 'custom';
        $('salesStart').value = formatDate(yesterday);
        $('salesEnd').value = formatDate(yesterday);
        $('salesRangeTrigger').textContent = `昨日 (${formatShortDate(yesterday)})`;
        return;
      }
      $('period').value = range;
      $('salesStart').value = '';
      $('salesEnd').value = '';
      $('salesRangeTrigger').textContent = range === '30d' ? '过去30天' : '过去7天';
    }
    function formatSalesRangeLabel() {
      const start = $('salesStart').value ? $('salesStart').value.replaceAll('-', '/') : '开始日期';
      const end = $('salesEnd').value ? $('salesEnd').value.replaceAll('-', '/') : '结束日期';
      return `${start} → ${end}`;
    }

    async function loadDefaultDateAndData() {
      setFilterValue('dateStart', '');
      setFilterValue('dateEnd', '');
      await loadAll();
    }
    function setFilterValue(id, value) {
      filterValues[id] = value;
      if ($(id)) $(id).value = value;
    }
    function getFilterValue(id) {
      const node = $(id);
      return node ? node.value.trim() : String(filterValues[id] || '').trim();
    }
    function derivePeriodFromCollectionDates() {
      const start = getFilterValue('dateStart');
      const end = getFilterValue('dateEnd');
      if (!start || !end) return '7d';
      const startTime = Date.parse(start);
      const endTime = Date.parse(end);
      if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime < startTime) return '7d';
      const days = Math.round((endTime - startTime) / 86400000) + 1;
      return [7, 30, 90, 180].includes(days) ? `${days}d` : '7d';
    }
    function collectionDateHint() {
      const start = getFilterValue('dateStart');
      const end = getFilterValue('dateEnd');
      if (!start && !end) return '全部采集日期';
      return `${start || '开始日期'} → ${end || '结束日期'}`;
    }

    async function loadAll() { await Promise.all([loadStats(), loadProducts()]); }
    function buildParams() {
      const derivedPeriod = derivePeriodFromCollectionDates();
      $('period').value = derivedPeriod;
      const params = new URLSearchParams({ source: state.source, page: state.page, page_size: state.pageSize, period: derivedPeriod, sort_by: state.sortBy, sort_order: state.sortOrder });
      Object.entries(paramMap).forEach(([id, key]) => {
        const value = getFilterValue(id);
        if (value) params.set(key, value);
      });
      if ($('period').value !== 'custom') {
        params.delete('sales_start');
        params.delete('sales_end');
      }
      return params;
    }

    async function loadProducts() {
      const res = await fetch(`/api/products?${buildParams()}`);
      const json = await res.json();
      if (!json.ok) return showToast(json.error || '加载失败');
      state.items = dedupeLatestItems(json.data.items || []);
      state.total = json.data.total;
      state.period = json.data.period;
      state.periodLabel = json.data.period_label || '近7天';
      $('periodHint').textContent = state.period === 'custom'
        ? `${state.periodLabel}销量，周期字段显示近7天数据`
        : `${state.periodLabel}数据`;
      renderItems();
      renderPager();
    }

    function dedupeLatestItems(items) {
      const latestByProduct = new Map();
      items.forEach(item => {
        const key = String(item.product_id || '');
        if (!key) return;
        const current = latestByProduct.get(key);
        if (!current || compareDateRecord(item.date_record, current.date_record) > 0) {
          latestByProduct.set(key, item);
        }
      });
      return Array.from(latestByProduct.values());
    }

    function compareDateRecord(left, right) {
      const leftTime = Date.parse(String(left || '').replaceAll('/', '-')) || 0;
      const rightTime = Date.parse(String(right || '').replaceAll('/', '-')) || 0;
      return leftTime - rightTime;
    }

    async function loadStats() {
      const params = buildParams();
      params.delete('page');
      params.delete('page_size');
      params.delete('period');
      params.delete('sort_by');
      params.delete('sort_order');
      const res = await fetch(`/api/products/stats?${params}`);
      const json = await res.json();
      if (!json.ok) return;
      const data = json.data;
      const cards = [
        ['待选中', data.statuses.PENDING || 0], ['待复核', data.statuses.REVIEWING || 0],
        ['待上架', data.statuses.READY || 0], ['已上架', data.statuses.PUBLISHED || 0],
        ['S/A/B', (data.ip_grades.S || 0) + (data.ip_grades.A || 0) + (data.ip_grades.B || 0)],
        ['工厂材质', data.materials['工厂材质'] || 0], ['非工厂材质', data.materials['非工厂材质'] || 0],
        ['疑似材质', (data.materials['疑似材质'] || 0) + (data.materials['其他疑似材质'] || 0)]
      ];
      $('stats').innerHTML = cards.map(([label, value]) => `<div class="stat"><span>${label}</span><strong>${value}</strong></div>`).join('');
    }

    function renderItems() {
      if (!state.items.length) { $('content').innerHTML = '<div class="empty">没有符合条件的商品</div>'; return; }
      state.view === 'cards' ? renderCards() : renderTable();
    }

    function renderTable() {
      const label = state.period === 'custom' ? '近7天' : state.periodLabel;
      $('content').innerHTML = `<div class="table-wrap"><table><thead><tr>
        <th>商品图</th><th>商品标题</th><th>${sortHeader('price', '价格')}</th><th>${sortHeader('rating', '评分')}</th><th>佣金比例</th>
        <th class="metric-header">${sortHeader('sold', '销量')}<small>采集日期范围内求和</small><span class="metric-hint" title="按当前选择的采集日期范围，对该商品每天的销量字段求和展示。">?</span></th><th>总销量</th><th>${sortHeader('sale_amount', `${label}成交额`)}</th><th>销售环比</th><th>${sortHeader('author_count', `${label}带货达人数`)}</th>
        <th>${label}视频成交占比</th><th>${label}商品卡成交占比</th><th>上架时间</th>
        <th>IP 等级</th><th>材质标签</th><th>当前状态</th><th>链接</th><th>详情</th>
      </tr></thead><tbody>${state.items.map(rowTemplate).join('')}</tbody></table></div>`;
      bindRowActions();
      bindSortHeaders();
    }

    function rowTemplate(item) {
      return `<tr>
        <td><button class="image-button" data-image="${item.image_url}" data-title="${escapeHtml(item.title)}"><img class="thumb" src="${item.image_url}" alt=""></button></td>
        <td class="title-cell">${productTitleCell(item)}</td>
        <td>${escapeHtml(item.price)}</td><td>${escapeHtml(item.rating)}</td><td>${escapeHtml(formatPlainNumber(item.commission_rate))}</td>
        <td>${escapeHtml(item.sold_count)}</td><td>${escapeHtml(item.total_sold_count)}</td><td>${escapeHtml(item.sale_amount)}</td><td>${escapeHtml(item.sales_growth)}</td>
        <td>${escapeHtml(item.author_count)}</td><td>${escapeHtml(item.video_ratio)}</td><td>${escapeHtml(item.product_card_ratio)}</td>
        <td>${escapeHtml(item.launch_time)}</td><td>${gradeBadge(item.ip_grade)}${reasonPreview('IP判断原因', item.ip_reason, item)}</td>
        <td>${materialBadge(item)}${reasonPreview('材质判断原因', item.material_reason)}</td>
        <td>${statusSelect(item)}</td><td>${platformLinks(item)}</td>
        <td><button data-detail="${item.product_id}" data-date="${item.date_record}">站内详情</button></td>
      </tr>`;
    }

    function renderCards() {
      const label = state.periodLabel;
      $('content').innerHTML = `<div class="card-grid">${state.items.map(item => `<article class="product-card">
        <button class="card-image-button" data-image="${item.image_url}" data-title="${escapeHtml(item.title)}"><img src="${item.image_url}" alt=""></button><div class="card-body"><h3>${escapeHtml(item.title)}</h3><p>${item.product_id} · ${item.date_record || ''}</p>
        <div class="badges">${gradeBadge(item.ip_grade)}${materialBadge(item)}</div>
        <div class="metrics"><span>价格 ${escapeHtml(item.price)}</span><span title="按当前选择的采集日期范围，对该商品每天的销量字段求和展示。">销量 ${escapeHtml(item.sold_count)}</span><span>总销量 ${escapeHtml(item.total_sold_count)}</span><span>${label}成交额 ${escapeHtml(item.sale_amount)}</span><span>达人 ${escapeHtml(item.author_count)}</span><span>视频 ${escapeHtml(item.video_ratio)}</span><span>商品卡 ${escapeHtml(item.product_card_ratio)}</span></div>
        <div class="card-actions">${statusSelect(item)}${platformLinks(item)}<button data-detail="${item.product_id}" data-date="${item.date_record}">站内详情</button></div></div>
      </article>`).join('')}</div>`;
      bindRowActions();
    }

    function sortHeader(field, label) {
      const active = state.sortBy === field;
      const arrow = active ? (state.sortOrder === 'asc' ? '↑' : '↓') : '↕';
      return `<button class="sort-header ${active ? 'active' : ''}" data-sort="${field}" type="button">${label}<span>${arrow}</span></button>`;
    }

    function bindSortHeaders() {
      document.querySelectorAll('[data-sort]').forEach(btn => {
        btn.addEventListener('click', () => {
          const field = btn.dataset.sort;
          if (state.sortBy === field) {
            state.sortOrder = state.sortOrder === 'asc' ? 'desc' : 'asc';
          } else {
            state.sortBy = field;
            state.sortOrder = 'desc';
            $('sortBy').value = field;
          }
          state.page = 1;
          loadProducts();
        });
      });
    }

    function bindRowActions() {
      document.querySelectorAll('[data-detail]').forEach(btn => btn.addEventListener('click', () => openDetail(btn.dataset.detail, btn.dataset.date)));
      document.querySelectorAll('[data-image]').forEach(btn => btn.addEventListener('click', () => openImage(btn.dataset.image, btn.dataset.title)));
      document.querySelectorAll('[data-reason-title]').forEach(btn => btn.addEventListener('click', () => openReason(btn.dataset.reasonTitle, btn.dataset.reasonText, btn.dataset.productTitle, btn.dataset.ipGrade)));
      document.querySelectorAll('[data-status-product]').forEach(select => select.addEventListener('change', () => updateStatus(select)));
      document.querySelectorAll('[data-product-title]').forEach(el => {
        el.addEventListener('mouseenter', () => showProductTitleTooltip(el));
        el.addEventListener('mousemove', () => positionProductTitleTooltip(el));
        el.addEventListener('mouseleave', hideProductTitleTooltip);
      });
      document.querySelectorAll('[data-copy-title]').forEach(btn => btn.addEventListener('click', event => {
        event.stopPropagation();
        event.preventDefault();
        copyProductTitle(btn);
      }));
      document.querySelectorAll('[data-translate-title]').forEach(btn => btn.addEventListener('click', event => {
        event.stopPropagation();
        event.preventDefault();
        translateProductTitle(btn);
      }));
    }

    function openImage(src, title) {
      $('largeImage').src = src;
      $('largeImage').alt = title || '商品图片';
      $('imageDialog').showModal();
    }

    let currentDetailItem = null;
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
        ['商品热度指数', detailValue(raw.viral_index)],
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
              <div class="detail-actions">${platformLinks(item)}</div>
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
    }

    function detailMetricCard(label, value) {
      return `<div class="detail-metric-card"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value || '--')}</strong></div>`;
    }

    function distributionPanel(title, distribution) {
      const items = Array.isArray(distribution) ? distribution : [];
      if (!items.length) return `<div class="detail-panel"><h5>${escapeHtml(title)}</h5><div class="detail-empty">暂无占比数据</div></div>`;
      return `<div class="detail-panel"><h5>${escapeHtml(title)}</h5><div class="detail-bars">${items.slice(0, 6).map((item, index) => {
        const name = item.name || item.type || item.label || `渠道${index + 1}`;
        const ratio = normalizePercent(item.percentage ?? item.ratio ?? item.value);
        const value = item.sales ?? item.sold ?? item.amount ?? item.count ?? '';
        return `<div class="detail-bar-row"><div><strong>${escapeHtml(name)}</strong><span>${escapeHtml(value)}</span></div><div class="detail-bar-track"><i style="width:${escapeHtml(ratio)}"></i></div><em>${escapeHtml(ratio)}</em></div>`;
      }).join('')}</div></div>`;
    }

    function overviewPanel(title, overview) {
      if (!overview || !Object.keys(overview).length) return `<div class="detail-panel"><h5>${escapeHtml(title)}</h5><div class="detail-empty">暂无周期指标</div></div>`;
      const baseKeys = Object.keys(overview).filter(k => !k.startsWith('日均') && overview[k] !== null && overview[k] !== '' && overview[k] !== undefined);
      if (!baseKeys.length) return `<div class="detail-panel"><h5>${escapeHtml(title)}</h5><div class="detail-empty">暂无周期指标</div></div>`;

      const html = baseKeys.slice(0, 10).map(key => {
        const val = overview[key];
        const dailyKey = `日均${key}`;
        const dailyVal = overview[dailyKey];
        let dailyHtml = '';
        if (dailyVal !== undefined && dailyVal !== null && dailyVal !== '') {
          dailyHtml = `<small style="display:block; color:#9ca3af; font-size:11px; margin-top:4px; font-weight:normal;">日均 ${escapeHtml(dailyVal)}</small>`;
        }
        return `<div><span>${escapeHtml(key)}</span><strong style="margin-top:6px;">${escapeHtml(val)}</strong>${dailyHtml}</div>`;
      }).join('');

      return `<div class="detail-panel"><h5>${escapeHtml(title)}</h5><div class="detail-kv-grid">${html}</div></div>`;
    }

    function materialAnalysisHtml(value) {
      const material = parseMaybeJson(value) || {};
      if (!Object.keys(material).length) return `<div class="detail-panel"><h5>材质分析</h5><div class="detail-empty">暂无材质分析</div></div>`;
      return `<div class="detail-panel"><h5>材质分析</h5><div class="detail-kv-grid">
        <div><span>类型</span><strong>${escapeHtml(material.material_type || '--')}</strong></div>
        <div><span>细分</span><strong>${escapeHtml(material.material_category || '--')}</strong></div>
        <div><span>置信度</span><strong>${escapeHtml(material.confidence || '--')}</strong></div>
      </div><p class="detail-note">${escapeHtml(material.material_reason || '')}</p></div>`;
    }

    function materialSummaryBadge(value) {
      const material = parseMaybeJson(value) || {};
      return material.material_type ? `<span>${escapeHtml(material.material_type)}</span>` : '';
    }

    function detailValue(...values) {
      for (const value of values) {
        if (value !== null && value !== undefined && value !== '') return String(value);
      }
      return '';
    }

    function parseMaybeJson(value) {
      if (!value) return null;
      if (typeof value === 'object') return value;
      try { return JSON.parse(value); } catch (error) { return null; }
    }

    function normalizePercent(value) {
      if (value === null || value === undefined || value === '') return '0%';
      const text = String(value);
      if (text.includes('%')) return text;
      const number = Number(text);
      if (!Number.isFinite(number)) return '0%';
      return `${number <= 1 ? Math.round(number * 100) : Math.round(number)}%`;
    }

    async function updateStatus(select) {
      const productId = select.dataset.statusProduct, dateRecord = select.dataset.statusDate, auditStatus = select.value;
      if (!confirm(`确认将该商品状态修改为「${statusText[auditStatus]}」？`)) { select.value = select.dataset.previous; return; }
      const res = await fetch(`/api/products/${state.source}/${productId}/status`, { method: 'PATCH', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({date_record: dateRecord, audit_status: auditStatus}) });
      const json = await res.json();
      if (!json.ok) { select.value = select.dataset.previous; return showToast(json.error || '状态修改失败'); }
      select.dataset.previous = auditStatus; showToast('状态已更新'); loadStats();
    }

    async function copyReadyLinks() {
      const res = await fetch(`/api/products/${state.source}/ready-links`);
      const json = await res.json();
      if (!json.ok) return showToast(json.error || '读取链接失败');
      await navigator.clipboard.writeText(json.data.links.join('\n'));
      showToast(`已复制 ${json.data.count} 条链接`);
    }

    function resetFilters() {
      Object.keys(paramMap).forEach(id => { if ($(id)) $(id).value = ''; });
      Object.keys(filterValues).forEach(id => delete filterValues[id]);
      activeFilterKeys.clear();
      renderActiveFilters();
      $('period').value = '7d'; $('sortBy').value = 'date_record'; state.sortBy = 'date_record'; state.sortOrder = 'desc'; $('salesStart').value = ''; $('salesEnd').value = ''; $('salesRangeTrigger').textContent = '过去7天'; $('salesCustomRange').classList.remove('visible'); state.page = 1; loadDefaultDateAndData();
    }

    function renderPager() {
      const maxPage = Math.max(1, Math.ceil(state.total / state.pageSize));
      $('pageInfo').textContent = `第 ${state.page} / ${maxPage} 页`;
      $('prevPage').disabled = state.page <= 1; $('nextPage').disabled = state.page >= maxPage;
    }

    function gradeBadge(grade) { return grade ? `<span class="badge grade-${grade}">IP ${grade}</span>` : '<span class="badge muted">未分析</span>'; }

    function materialBadge(item) { return item.material_type ? `<span class="badge material">${escapeHtml(item.material_type)}${item.material_category ? ' · ' + escapeHtml(item.material_category) : ''}</span>` : '<span class="badge muted">材质未分析</span>'; }

    function reasonPreview(title, text, item = {}) {
      return `<button type="button" class="reason-preview" data-reason-title="${escapeHtml(title)}" data-reason-text="${escapeHtml(text || '')}" data-product-title="${escapeHtml(item.title || '')}" data-ip-grade="${escapeHtml(item.ip_grade || '')}">${escapeHtml(shortText(text, 42) || '暂无详细原因')}</button>`;
    }

    function formatIpReasonHtml(text, item = {}) {
      const reason = String(text || '').trim();
      const grade = item.ip_grade || inferIpGrade(reason);
      const summary = summarizeIpReason(reason, item.title || '', grade);
      return `<section class="ip-analysis">
        <div class="ip-analysis-head">
          <span>${gradeBadge(grade)}</span>
          <strong>${escapeHtml(summary.risk)}</strong>
        </div>
        <div class="ip-conclusion"><strong>结论：</strong>${escapeHtml(summary.conclusion)}</div>
        <div class="ip-section"><h4>1. 插画/图案出自哪里？</h4><p>${escapeHtml(summary.source)}</p></div>
        <div class="ip-section"><h4>2. 是否构成侵权风险？</h4><p>${escapeHtml(summary.riskDetail)}</p></div>
        <div class="ip-section"><h4>3. 分析原因</h4>${reasonListHtml(summary.reasons)}</div>
        <details class="ip-raw"><summary>查看原始分析</summary><pre>${escapeHtml(reason || '暂无详细原因')}</pre></details>
      </section>`;
    }

    function summarizeIpReason(reason, title, grade) {
      const allText = `${title || ''} ${reason || ''}`;
      const risk = inferRiskLabel(reason, grade);
      return {
        risk,
        conclusion: pickConclusion(reason, grade, risk),
        source: inferIllustrationSource(allText, grade),
        riskDetail: inferRiskDetail(reason, grade),
        reasons: buildReasonGroups(reason, title),
      };
    }

    function inferIpGrade(reason) {
      const match = String(reason || '').match(/[IP\s]*([SABCDE])级|判定为\s*([SABCDE])|IP\s*([SABCDE])/i);
      return match ? (match[1] || match[2] || match[3]).toUpperCase() : '';
    }

    function inferRiskLabel(reason, grade) {
      const text = String(reason || '');
      if (/[高高]风险|侵权风险高|高度疑似|未授权|商标风险|版权风险/.test(text) || ['S', 'A', 'B'].includes(grade)) return '高风险，建议拦截或人工复核';
      if (/无风险|无IP风险|非IP|通用|E级/.test(text) || grade === 'E') return '低风险，未识别到明确 IP 指向';
      if (['C', 'D'].includes(grade)) return '中低风险，建议结合图片复核';
      return '待复核，需要结合图片和标题确认';
    }

    function pickConclusion(reason, grade, risk) {
      const text = String(reason || '').replace(/\s+/g, ' ');
      const explicit = text.match(/(?:综合判定|结论)[:：]?\s*([^。；\n]{12,160})/);
      if (explicit) return tidySentence(explicit[1]);
      if (/豹纹|Leopard/i.test(text)) return '该商品更像通用豹纹装饰手机壳，标题与卖点未指向特定受保护 IP。';
      if (/Adventure Time|探险活宝/i.test(text)) return '商品图案明显指向《Adventure Time / 探险活宝》相关角色，未授权销售风险较高。';
      return `${risk}${grade ? `，当前判定为 IP ${grade}。` : '。'}`;
    }

    function inferIllustrationSource(text, grade) {
      const sourcePatterns = [
        [/Adventure Time|探险活宝/i, 'Cartoon Network 动画《Adventure Time / 探险活宝》'],
        [/Disney|迪士尼|Mickey|Minnie|米老鼠/i, 'Disney / 迪士尼相关角色或标识'],
        [/Barbie|芭比/i, 'Mattel 旗下 Barbie / 芭比品牌'],
        [/Power Rangers|恐龙战队|超凡战队/i, '《Power Rangers / 恐龙战队》系列'],
        [/BAPE|A Bathing Ape|猿人头/i, 'A Bathing Ape / BAPE 品牌视觉资产'],
        [/Burberry|博柏利|巴宝莉/i, 'Burberry 经典格纹视觉资产'],
      ];
      for (const [pattern, source] of sourcePatterns) {
        if (pattern.test(text)) return source;
      }
      if (/豹纹|Leopard/i.test(text)) return '未识别到特定插画/IP来源；图案更接近自然豹纹这一通用装饰元素。';
      if (/通用|非IP|无IP风险|E级/.test(text) || grade === 'E') return '未识别到明确的版权角色、品牌 Logo 或特定作品来源。';
      return '暂未从标题或分析文本中提取到明确来源，需要结合商品图片人工确认。';
    }

    function inferRiskDetail(reason, grade) {
      const text = String(reason || '');
      if (/无风险|无IP风险|非IP|通用|E级/.test(text) || grade === 'E') return '目前依据标题、卖点和规则库结果看，主要是产品类型、功能或通用图案描述，未命中特定 IP 关键词。';
      if (/未授权|侵权风险高|商标风险|版权风险|高风险/.test(text) || ['S', 'A', 'B'].includes(grade)) return '如果没有授权，应按高风险处理；核心风险来自角色形象、作品名、品牌标识或商标元素的直接使用。';
      return '风险点不够明确，建议重点核对图片中是否存在可识别角色、品牌标志、作品名或专属图案。';
    }

    function buildReasonGroups(reason, title) {
      const sentences = splitReasonSentences(reason);
      const imageItems = sentences.filter(item => /图片|图里|图中|外观|图案|角色|形象|Logo|字样|插画|豹纹/.test(item)).slice(0, 4);
      const ruleItems = sentences.filter(item => /规则库|经验库|比对|命中|未出现|未识别/.test(item)).slice(0, 3);
      const fallback = sentences.filter(item => !imageItems.includes(item) && !ruleItems.includes(item)).slice(0, 3);
      return [
        { title: '图片角度', items: imageItems.length ? imageItems : ['从当前分析文本看，图片未提取到明确受保护角色、品牌 Logo 或作品名。'] },
        { title: '标题角度', items: titleKeywordFallback(title, fallback) },
        { title: '规则/经验依据', items: ruleItems.length ? ruleItems : ['未命中明确规则库 IP 关键词时，按通用商品描述或低风险候选处理。'] },
      ];
    }

    function titleKeywordFallback(title, fallback) {
      if (title) return [`标题：${shortText(title, 120)}`].concat(fallback.slice(0, 2));
      return fallback.length ? fallback : ['标题中未提取到明确 IP 关键词。'];
    }

    function reasonListHtml(groups) {
      return groups.map(group => `<div class="ip-reason-group"><strong>${escapeHtml(group.title)}</strong><ul>${group.items.map(item => `<li>${escapeHtml(shortText(tidySentence(item), 180))}</li>`).join('')}</ul></div>`).join('');
    }

    function splitReasonSentences(reason) {
      return String(reason || '')
        .replace(/\s+/g, ' ')
        .split(/(?<=[。；;])|\(\d+\)|（\d+）/)
        .map(tidySentence)
        .filter(item => item.length >= 8);
    }

    function tidySentence(text) {
      return String(text || '').replace(/^[：:；;，,\s]+/, '').replace(/[。；;，,\s]+$/, '').trim();
    }

    function platformLinks(item) {
      return `<div class="platform-links">${sourcePlatformLink(item)}${tiktokPlatformLink(item)}</div>`;
    }

    function sourcePlatformLink(item) {
      const platform = state.source === 'kalodata' ? 'kalodata' : 'fastmoss';
      const url = item.platform_url || (platform === 'fastmoss' ? buildFastMossUrl(item.product_id) : '');
      if (!url) return '<span class="platform-link disabled" title="暂无商品链接">--</span>';
      const label = platform === 'kalodata' ? 'Kalodata' : 'FastMoss';
      return `<a class="platform-link ${platform}" href="${escapeHtml(url)}" target="_blank" rel="noreferrer" title="点击跳转到 ${label} 商品详情页" aria-label="点击跳转到 ${label} 商品详情页">${platformLogo(platform)}</a>`;
    }

    function tiktokPlatformLink(item) {
      const url = buildTikTokShopUrl(item.product_id);
      return `<a class="platform-link tiktok" href="${escapeHtml(url)}" target="_blank" rel="noreferrer" title="点击跳转到 TikTok 商品详情页" aria-label="点击跳转到 TikTok 商品详情页">${platformLogo('tiktok')}</a>`;
    }

    function platformLogo(platform) {
      if (platform === 'tiktok') {
        return `<svg class="platform-logo tiktok-logo" viewBox="0 0 448 512" aria-hidden="true">
          <path d="M448 210a210 210 0 0 1-123-39v178a163 163 0 1 1-140-161v90a75 75 0 1 0 52 71V0h88a121 121 0 0 0 2 22 122 122 0 0 0 54 80 121 121 0 0 0 67 20z"></path>
        </svg>`;
      }
      if (platform === 'kalodata') {
        return `<svg class="platform-logo kalodata-logo" viewBox="0 0 32 32" aria-hidden="true">
          <rect x="5" y="5" width="22" height="22" rx="6"></rect>
          <path d="M11 22V10"></path><path d="M21 10l-8 8"></path><path d="M16 16l6 6"></path>
        </svg>`;
      }
      return `<svg class="platform-logo fastmoss-logo" viewBox="0 0 32 32" aria-hidden="true">
        <path d="M8 23V9h10"></path><path d="M8 16h9"></path><path d="M20 23V11l4 7 4-7v12"></path>
      </svg>`;
    }

    function buildFastMossUrl(productId) {
      return `https://www.fastmoss.com/zh/e-commerce/detail/${encodeURIComponent(productId)}`;
    }

    function productTitleCell(item) {
      const title = item.title || '';
      return `<div class="product-title-row">
        <button type="button" class="product-title-text" data-product-title="${escapeHtml(title)}">${escapeHtml(title)}</button>
        <div class="product-title-actions">
          <button type="button" class="icon-button" data-copy-title="${escapeHtml(title)}" title="Copy title" aria-label="Copy title">
            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="10" height="10" rx="1.5"></rect><path d="M5 15H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v1"></path></svg>
          </button>
          <button type="button" class="icon-button" data-translate-title="${escapeHtml(title)}" title="Translate to Chinese" aria-label="Translate to Chinese">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h9"></path><path d="M9 3v2c0 4-2 7-5 9"></path><path d="M5 9c1.1 2.2 3 4.1 5.5 5.5"></path><path d="M12 20l4-9 4 9"></path><path d="M14 16h4"></path></svg>
          </button>
        </div>
      </div><span>${escapeHtml(item.product_id)} · ${escapeHtml(item.date_record || '')}</span><small class="title-translation"></small>`;
    }

    function ensureProductTitleTooltip() {
      let tooltip = $('productTitleTooltip');
      if (!tooltip) {
        tooltip = document.createElement('div');
        tooltip.id = 'productTitleTooltip';
        tooltip.className = 'product-title-tooltip';
        document.body.appendChild(tooltip);
      }
      return tooltip;
    }

    function showProductTitleTooltip(el) {
      const tooltip = ensureProductTitleTooltip();
      tooltip.textContent = el.dataset.productTitle || '';
      tooltip.classList.add('show');
      positionProductTitleTooltip(el);
    }

    function positionProductTitleTooltip(el) {
      const tooltip = ensureProductTitleTooltip();
      const rect = el.getBoundingClientRect();
      const left = Math.min(Math.max(12, rect.left), window.innerWidth - tooltip.offsetWidth - 12);
      const top = Math.max(12, rect.top - tooltip.offsetHeight - 10);
      tooltip.style.left = `${left}px`;
      tooltip.style.top = `${top}px`;
    }

    function hideProductTitleTooltip() {
      const tooltip = $('productTitleTooltip');
      if (tooltip) tooltip.classList.remove('show');
    }

    function copyProductTitle(btn) {
      const text = btn.dataset.copyTitle || '';
      if (copyTextLegacy(text)) {
        showToast('商品名已复制');
        return;
      }
      if (navigator.clipboard) {
        navigator.clipboard.writeText(text)
          .then(() => showToast('商品名已复制'))
          .catch(() => showToast('复制失败，请手动选中后复制'));
        return;
      }
      showToast('复制失败，请手动选中后复制');
    }

    function copyTextLegacy(text) {
      const input = document.createElement('textarea');
      input.value = text;
      input.setAttribute('readonly', '');
      input.style.position = 'fixed';
      input.style.top = '0';
      input.style.left = '-9999px';
      input.style.opacity = '0';
      document.body.appendChild(input);
      input.focus();
      input.select();
      input.setSelectionRange(0, input.value.length);
      let ok = false;
      try {
        ok = document.execCommand('copy');
      } catch (error) {
        ok = false;
      }
      document.body.removeChild(input);
      return ok;
    }

    async function translateProductTitle(btn) {
      const title = btn.dataset.translateTitle || '';
      const target = btn.closest('.title-cell')?.querySelector('.title-translation');
      btn.disabled = true;
      if (target) target.textContent = '翻译中...';
      try {
        const res = await fetch('/api/translate-title', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({text: title}),
        });
        const json = await res.json();
        if (!json.ok) throw new Error(json.error || '翻译失败');
        if (target) target.textContent = json.data.translation;
        showToast('已翻译成中文');
      } catch (error) {
        if (target) target.textContent = '';
        showToast(error.message || '翻译失败');
      } finally {
        btn.disabled = false;
      }
    }

    function buildTikTokShopUrl(productId) {
      const slug = 'slug';
      return `https://www.tiktok.com/shop/pdp/${slug}/${productId}?source=ecommerce_store&region=US`;
    }

    function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'}[ch])); }
    function showToast(text) { $('toast').textContent = text; $('toast').classList.add('show'); setTimeout(() => $('toast').classList.remove('show'), 2200); }

    setSalesRangePreset('7d');
    loadDefaultDateAndData();
  