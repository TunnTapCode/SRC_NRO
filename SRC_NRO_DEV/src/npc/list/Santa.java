package npc.list;

import consts.ConstNpc;
import item.Item;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import npc.Npc;
import player.Player;
import shop.ShopService;
import services.func.Input;
import player.Service.InventoryService;

public class Santa extends Npc {

    public Santa(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    /**
     * Map duoc phep mo "Shop Vip" (tag SHOP_VIP trong bang shop, shop id 31).
     * De trong {}  = moi map co NPC Santa deu mo duoc Shop Vip.
     * Muon gioi han: dien mapId vao day, vi du { 5, 20, 156 }.
     */
    private static final int[] MAP_SHOP_VIP = {5, 20, 156 };

    /** NPC Santa o map hien tai co duoc hien/cho mo Shop Vip khong */
    private boolean canOpenShopVip() {
        if (MAP_SHOP_VIP.length == 0) {
            return true;
        }
        for (int mapIdShopVip : MAP_SHOP_VIP) {
            if (mapIdShopVip == this.mapId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {

            Item pGG = InventoryService.gI().findItem(player.inventory.itemsBag, 459);
            int soLuong = 0;
            if (pGG != null) {
                soLuong = pGG.quantity;
            }
            List<String> menu = new ArrayList<>(Arrays.asList(
                    "Cửa hàng",
                    "Mở rộng\nHành trang\nRương đồ",
                    "Nhập mã\nquà tặng",
                    // "Cửa hàng\nHạn sử dụng",
                    "Tiệm\nHớt tóc"
                    // ,
                    // "Danh\nhiệu",
            ));

            if (soLuong >= 1) {
                menu.add(1, "Giảm giá\n80%");
            }

            // Shop Vip luon nam cuoi menu (khong co phieu giam gia: chi so 4, co phieu: chi so 5)
            if (canOpenShopVip()) {
                menu.add("Shop Vip");
            }

            String[] menus = menu.toArray(new String[0]);

            createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Xin chào, ta có một số vật phẩm đặc biệt cậu có muốn xem không?", menus);
        }

    }
@Override
public void confirmMenu(Player player, int select) {
    if (!canOpenNpc(player)) return;

    Item pGG = InventoryService.gI().findItem(player.inventory.itemsBag, 459);
    boolean hasVoucher = pGG != null && pGG.quantity > 0;

    if (!player.idMark.isBaseMenu()) return;

    if (hasVoucher) {
        switch (select) {
            case 0:
                ShopService.gI().opendShop(player, "SANTA", false);
                break;
            case 1:
                ShopService.gI().opendShop(player, "SANTA_GIAM_GIA", false);
                break;
            case 2:
                ShopService.gI().opendShop(player, "SANTA_MO_RONG_HANH_TRANG", false);
                break;
            case 3:
                Input.gI().createFormGiftCode(player);
                break;
            case 4:
                ShopService.gI().opendShop(player, "SANTA_HEAD", false);
                break;
            case 5:
                if (canOpenShopVip()) {
                    ShopService.gI().opendShop(player, "SHOP_VIP", false);
                }
                break;
        }
    } else {
        switch (select) {
            case 0:
                ShopService.gI().opendShop(player, "SANTA", false);
                break;
            case 1:
                ShopService.gI().opendShop(player, "SANTA_MO_RONG_HANH_TRANG", false);
                break;
            case 2:
                Input.gI().createFormGiftCode(player);
                break;
            case 3:
                ShopService.gI().opendShop(player, "SANTA_HEAD", false);
                break;
            case 4:
                if (canOpenShopVip()) {
                    ShopService.gI().opendShop(player, "SHOP_VIP", false);
                }
                break;
        
    

    // @Override
    // public void confirmMenu(Player player, int select) {
    //     if (canOpenNpc(player)) {
    //         Item pGG = InventoryService.gI().findItem(player.inventory.itemsBag, 459);
    //         int soLuong = 0;
    //         if (pGG != null) {
    //             soLuong = pGG.quantity;
    //         }

    //         if (this.mapId == 5 || this.mapId == 13 || this.mapId == 20) {
    //             if (player.idMark.isBaseMenu()) {
    //                 switch (select) {
    //                     case 0:
    //                         ShopService.gI().opendShop(player, "SANTA", false);
    //                         break;
    //                     case 1:
    //                         if (soLuong >= 1) {
    //                             ShopService.gI().opendShop(player, "SANTA_GIAM_GIA", false);
    //                         } else {
    //                             ShopService.gI().opendShop(player, "SANTA_MO_RONG_HANH_TRANG", false);
    //                         }
    //                         break;
    //                     case 2:
    //                         if (soLuong >= 1) {
    //                             ShopService.gI().opendShop(player, "SANTA_MO_RONG_HANH_TRANG", false);
    //                         } else {
    //                               Input.gI().createFormGiftCode(player);
    //                         }
    //                         break;
    //                     case 3:
    //                         if (soLuong >= 1) {
    //                               Input.gI().createFormGiftCode(player);
    //                         } else {
    //                             ShopService.gI().opendShop(player, "SANTA_HEAD",false);
    //                         }
    //                         break;

                        
    //                     // case 4:
    //                     //     if (soLuong >= 1) {
    //                     //         ShopService.gI().opendShop(player, "SANTA_HAN_SU_DUNG", false);
    //                     //     } else {
    //                             // ShopService.gI().opendShop(player, "SANTA_HEAD", false);
    //                     //     }
    //                     //     break;
    //                     // case 5:
    //                     //     if (soLuong >= 1) {
    //                     //         ShopService.gI().opendShop(player, "SANTA_HEAD", false);
    //                     //     } else {
    //                     //         ShopService.gI().opendShop(player, "SANTA_DANH_HIEU", false);
    //                     //     }
    //                     //     break;
    //                     // case 6:
    //                     //     if (soLuong >= 1) {
    //                     //         ShopService.gI().opendShop(player, "SANTA_DANH_HIEU", false);
    //                     //     } else {
    //                     //         ShopService.gI().opendShop(player, "SHOP_VIP", false);
    //                     //     }                            
    //                     //     break;
    //                     // case 7:
    //                     //     ShopService.gI().opendShop(player, "SHOP_VIP", false);
    //                     //     break;
    //                            case 4:
    //                          ShopService.gI().opendShop(player, "SANTA_HEAD", false);
    //                          break;
    //                 }
                }
            }
        }
    }

