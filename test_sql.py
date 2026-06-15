import app
from flask import Flask

# Hack to mock the request and see the SQL
app.app.config['TESTING'] = True
with app.app.test_request_context('/api/products?source=kalodata&page=1&page_size=30&period=7d&sort_by=product_card_sales&sort_order=desc&date_start=2026-06-08&date_end=2026-06-08'):
    source = app.normalize_source("kalodata")
    period = app.normalize_period("7d")
    sales_period = app.build_sales_period(period, "2026-06-08", "2026-06-08")
    meta = app.SOURCES[source]
    
    # Mock where
    where = ["date_record BETWEEN %s AND %s"]
    params = ["2026-06-08", "2026-06-08"]
    
    use_sales_aggregate = True
    order_sql = app.build_order(meta, "product_card_sales", "desc", sales_period, use_sales_aggregate, app.request.args)
    
    latest_join = app.build_latest_product_join(meta, where)
    sales_aggregate_join = app.build_sales_aggregate_join(meta, where)
    previous_sales_aggregate_join = app.build_previous_sales_aggregate_join(meta, "2026-06-08", "2026-06-08")
    sales_delta_join = app.build_sales_delta_join(meta, "2026-06-08", "2026-06-08")
    
    sql = f"""
        SELECT inner_id
        FROM (
            SELECT `{meta["table"]}`.`id` AS inner_id
            FROM `{meta["table"]}`
            {latest_join}
            {sales_aggregate_join}
            {previous_sales_aggregate_join}
            {sales_delta_join}
            {order_sql}
            LIMIT %s OFFSET %s
        ) AS paginated
        JOIN `{meta["table"]}` ON `{meta["table"]}`.`id` = paginated.inner_id
        {latest_join}
        {sales_aggregate_join}
        {previous_sales_aggregate_join}
        {sales_delta_join}
        {order_sql}
    """
    
    print("----- SQL -----")
    print(sql)
    
    print("----- PARAMS COUNT -----")
    print("Count of %s in SQL:", sql.count("%s"))
    
    query_params = [*params]
    if use_sales_aggregate:
        query_params.extend(params)
    execute_params = [*query_params, *query_params, 30, 0]
    
    print("Execute params len:", len(execute_params))
    print("Execute params:", execute_params)
