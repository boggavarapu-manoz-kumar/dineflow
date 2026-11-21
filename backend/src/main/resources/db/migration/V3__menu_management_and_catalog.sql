-- ==============================================================================
-- Flyway Database Migration: V3__menu_management_and_catalog.sql
-- Description: Core schema for branches, menu categories, food items,
--              item images, variants, option groups, options, and branch overrides.
-- ==============================================================================

-- 1. Branches Table (Multi-unit support under tenant)
CREATE TABLE IF NOT EXISTS branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(32) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(32),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_branches_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_branches_tenant ON branches(tenant_id);

-- 2. Menu Categories Table
CREATE TABLE IF NOT EXISTS menu_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    icon_name VARCHAR(64),
    image_url VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_categories_tenant_normalized_name UNIQUE (tenant_id, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_categories_tenant_order ON menu_categories(tenant_id, display_order);
CREATE INDEX IF NOT EXISTS idx_categories_tenant_active ON menu_categories(tenant_id, is_active);

-- 3. Menu Items (Food Items) Table
CREATE TABLE IF NOT EXISTS menu_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES menu_categories(id) ON DELETE RESTRICT,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    short_description VARCHAR(255),
    detailed_description TEXT,
    base_price NUMERIC(10, 2) NOT NULL CHECK (base_price >= 0),
    dietary_classification VARCHAR(32) NOT NULL DEFAULT 'VEGETARIAN', -- VEGETARIAN, NON_VEGETARIAN, VEGAN, CONTAINS_EGG, JAIN
    preparation_time_minutes INT DEFAULT 15,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_menu_items_tenant_normalized_name UNIQUE (tenant_id, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_menu_items_category ON menu_items(category_id);
CREATE INDEX IF NOT EXISTS idx_menu_items_tenant_order ON menu_items(tenant_id, display_order);
CREATE INDEX IF NOT EXISTS idx_menu_items_tenant_dietary ON menu_items(tenant_id, dietary_classification);
CREATE INDEX IF NOT EXISTS idx_menu_items_tenant_avail ON menu_items(tenant_id, is_active, is_available);

-- 4. Menu Item Images Table
CREATE TABLE IF NOT EXISTS menu_item_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    image_url VARCHAR(1024) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_menu_item_images_item ON menu_item_images(menu_item_id);

-- 5. Menu Variants Table
CREATE TABLE IF NOT EXISTS menu_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_variants_item_name UNIQUE (menu_item_id, name)
);

CREATE INDEX IF NOT EXISTS idx_menu_variants_item ON menu_variants(menu_item_id);

-- 6. Menu Option Groups Table
CREATE TABLE IF NOT EXISTS menu_option_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    min_selections INT NOT NULL DEFAULT 0,
    max_selections INT NOT NULL DEFAULT 1,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_option_groups_tenant ON menu_option_groups(tenant_id);

-- 7. Menu Options (Add-ons / Modifiers) Table
CREATE TABLE IF NOT EXISTS menu_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    option_group_id UUID NOT NULL REFERENCES menu_option_groups(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    additional_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (additional_price >= 0),
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_menu_options_group ON menu_options(option_group_id);

-- 8. Menu Item Option Groups Join Table
CREATE TABLE IF NOT EXISTS menu_item_option_groups (
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    option_group_id UUID NOT NULL REFERENCES menu_option_groups(id) ON DELETE CASCADE,
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (menu_item_id, option_group_id)
);

-- 9. Branch-Level Menu Item Configurations (Price Overrides & Stock Availability)
CREATE TABLE IF NOT EXISTS menu_item_branch_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    price_override NUMERIC(10, 2) CHECK (price_override >= 0),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_branch_menu_item UNIQUE (branch_id, menu_item_id)
);

CREATE INDEX IF NOT EXISTS idx_branch_configs_lookup ON menu_item_branch_configs(branch_id, menu_item_id);
