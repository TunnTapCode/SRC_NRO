// Cấu hình metadata cho từng resource: tên API và các cột hiển thị trong bảng
const RESOURCE_META = {
    accounts:  { resource: 'accounts',  columns: ['id', 'username', 'email', 'is_admin', 'active', 'ban'] },
    players:   { resource: 'players',   columns: ['id', 'account_id', 'name', 'head', 'gender', 'clan_id', 'rank', 'power'] },
    giftcodes: { resource: 'giftcodes', columns: ['id', 'code', 'count_left', 'detail', 'expired'] },
    items:     { resource: 'items',     columns: ['id', 'TYPE', 'NAME', 'description', 'level', 'icon_id', 'part', 'is_up_to_up', 'power_require', 'gold', 'gem', 'head', 'body', 'leg'] },
    shops:     { resource: 'shops',     columns: ['id', 'npc_id', 'tag_name', 'type_shop'] },
    npcs:      { resource: 'npcs',      columns: ['id', 'NAME', 'head', 'body', 'leg', 'avatar'] }
};

// Cấu hình form thêm / sửa cho từng resource
const FORM_CONFIG = {
    accounts: [
        { name: 'username',  label: 'Username',   type: 'text',     required: true },
        { name: 'email',     label: 'Email',      type: 'email',    required: true },
        { name: 'password',  label: 'Mật khẩu',  type: 'password', placeholder: 'Để trống nếu giữ nguyên' },
        { name: 'is_admin',  label: 'Admin',      type: 'select',   options: [{ value: '1', text: 'Có' }, { value: '0', text: 'Không' }] },
        { name: 'active',    label: 'Active',     type: 'select',   options: [{ value: '1', text: 'Có' }, { value: '0', text: 'Không' }] },
        { name: 'ban',       label: 'Ban',        type: 'select',   options: [{ value: '1', text: 'Có' }, { value: '0', text: 'Không' }] }
    ],
    players: [
        { name: 'account_id', label: 'Account ID', type: 'number', required: true },
        { name: 'name',       label: 'Tên',        type: 'text',   required: true },
        { name: 'head',       label: 'Head',       type: 'number', required: true },
        { name: 'gender',     label: 'Gender',     type: 'number' },
        { name: 'clan_id',    label: 'Clan ID',    type: 'number' },
        { name: 'rank',       label: 'Rank',       type: 'number' },
        { name: 'power',      label: 'Power',      type: 'number' }
    ],
    giftcodes: [
        { name: 'code',       label: 'Code',          type: 'text',   required: true },
        { name: 'count_left', label: 'Số lượng còn', type: 'number', required: true },
        { name: 'detail',     label: 'Detail',        type: 'text' },
        { name: 'expired',    label: 'Expired',       type: 'text' }
    ],
    items: [
        { name: 'TYPE',          label: 'TYPE',        type: 'text',   required: true },
        { name: 'NAME',          label: 'NAME',        type: 'text',   required: true },
        { name: 'description',   label: 'Description', type: 'text' },
        { name: 'level',         label: 'Level',       type: 'number' },
        { name: 'icon_id',       label: 'Icon ID',     type: 'number' },
        { name: 'part',          label: 'Part',        type: 'number' },
        { name: 'is_up_to_up',   label: 'Up to up',   type: 'number' },
        { name: 'power_require', label: 'Power req',  type: 'number' },
        { name: 'gold',          label: 'Gold',        type: 'number' },
        { name: 'gem',           label: 'Gem',         type: 'number' },
        { name: 'head',          label: 'Head',        type: 'number' },
        { name: 'body',          label: 'Body',        type: 'number' },
        { name: 'leg',           label: 'Leg',         type: 'number' }
    ],
    shops: [
        { name: 'npc_id',    label: 'NPC ID',    type: 'number', required: true },
        { name: 'tag_name',  label: 'Tag name',  type: 'text',   required: true },
        { name: 'type_shop', label: 'Type shop', type: 'text',   required: true }
    ],
    npcs: [
        { name: 'NAME',   label: 'NAME',   type: 'text' },
        { name: 'head',   label: 'Head',   type: 'number' },
        { name: 'body',   label: 'Body',   type: 'number' },
        { name: 'leg',    label: 'Leg',    type: 'number' },
        { name: 'avatar', label: 'Avatar', type: 'number' }
    ]
};
