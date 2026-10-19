package app.exteraless.icons;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.DocumentSelectActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.exteraless.icons.ui.NewIconPackBottomSheet;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Экран управления паками иконок (порт com.exteragram.messenger.icons.ui.IconPacksActivity).
 *
 * Подключается к общим настройкам по полному имени {@code app.exteraless.icons.IconPacksActivity}.
 */
public class IconPacksActivity extends BaseNekoSettingsActivity {

    private int basePacksHeaderRow;
    private int baseDefaultRow;
    private int baseSolarRow;
    private int baseRemixRow;
    private int baseHalloweenRow;
    private int basePacksDividerRow;

    private int enableHeaderRow;
    private int enableRow;
    private int enableDividerRow;

    private int packsHeaderRow;
    private int packsStartRow;
    private int packsEndRow;
    private int installRow;
    private int createRow;
    private int packsDividerRow;

    private int editingRow;
    private int editingDividerRow;

    private final List<IconPack> packs = new ArrayList<>();

    public IconPacksActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        IconPacksConfig.init();
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        packs.clear();
        packs.addAll(sortByPriority(IconPackStorage.getInstalledPacks()));

        // Секция «Базовые наборы» — порт IconPacksActivity.fillItems:254 (заголовок BasePacks).
        // В exteraGram активен ровно один встроенный набор: первый id "base.*" в iconPacksLayout,
        // по умолчанию "base.default". У нас выбор лежит в NaConfig.iconReplacements
        // (0/1/2), см. BaseIconPacks — отдельного ключа под это заводить не стали.
        basePacksHeaderRow = addRow("iconPacksBase");
        baseDefaultRow = addRow("iconPackBaseDefault");
        baseSolarRow = addRow("iconPackBaseSolar");
        baseRemixRow = addRow("iconPackBaseRemix");
        baseHalloweenRow = app.exteraless.appearance.AppearanceConfig.halloweenTheme() ? addRow("iconPackBaseHalloween") : -1;
        basePacksDividerRow = addRow();

        enableHeaderRow = addRow("iconPacksHeader");
        enableRow = addRow("iconPacksEnabled");
        enableDividerRow = addRow();

        packsHeaderRow = addRow("iconPacksList");
        if (packs.isEmpty()) {
            packsStartRow = -1;
            packsEndRow = -1;
        } else {
            packsStartRow = rowCount;
            rowCount += packs.size();
            packsEndRow = rowCount;
        }
        installRow = addRow("iconPacksInstall");
        createRow = addRow("iconPacksCreate");
        packsDividerRow = addRow();

        if (IconPacksConfig.isEditing()) {
            editingRow = addRow("iconPacksEditing");
            editingDividerRow = addRow();
        } else {
            editingRow = editingDividerRow = -1;
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.IconPacks);
    }

    @Override
    protected String getKey() {
        return "iconpacks";
    }

    /**
     * Активные паки — вперёд, в своём порядке; остальные следом.
     *
     * Порядок активных и есть приоритет при конфликте имён иконок: побеждает
     * первый (IconPacksConfig.activePacks — упорядоченный список). Поэтому
     * перетаскивание должно быть видимым, а для этого активные обязаны идти
     * подряд и сверху.
     */
    private List<IconPack> sortByPriority(List<IconPack> installed) {
        List<String> active = IconPacksConfig.getActivePackIds();
        List<IconPack> result = new ArrayList<>();
        for (String id : active) {
            for (IconPack pack : installed) {
                if (pack.getId().equals(id)) {
                    result.add(pack);
                    break;
                }
            }
        }
        for (IconPack pack : installed) {
            if (!active.contains(pack.getId())) {
                result.add(pack);
            }
        }
        return result;
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == baseDefaultRow || position == baseSolarRow || position == baseRemixRow || position == baseHalloweenRow) {
            int type = position == baseSolarRow ? BaseIconPacks.BASE_SOLAR
                    : position == baseRemixRow ? BaseIconPacks.BASE_REMIX
                    : position == baseHalloweenRow ? BaseIconPacks.BASE_HALLOWEEN
                    : BaseIconPacks.BASE_DEFAULT;
            if (type == BaseIconPacks.getSelected()) {
                return;
            }
            BaseIconPacks.setSelected(type);
            if (listAdapter != null) {
                listAdapter.notifyItemRangeChanged(baseDefaultRow, baseHalloweenRow != -1 ? 4 : 3);
            }
            IconPackManager.getInstance().reload();
            // IconsResources кэширует выбранный набор до перезапуска процесса
            showRestartHint();
        } else if (position == enableRow) {
            boolean value = IconPacksConfig.enabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(value);
            }
            IconPackManager.getInstance().reload();
            showRestartHint();
        } else if (position == installRow) {
            openArchivePicker();
        } else if (position == createRow) {
            showPackInfoSheet(null);
        } else if (editingRow != -1 && position == editingRow) {
            app.exteraless.icons.picker.IconPickerController.finishEditing();
            updateRowsAndNotify();
        } else if (packsStartRow != -1 && position >= packsStartRow && position < packsEndRow) {
            IconPack pack = packs.get(position - packsStartRow);
            boolean active = IconPacksConfig.togglePack(pack.getId());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(active);
            }
            IconPackManager.getInstance().reload();
            showRestartHint();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (packsStartRow != -1 && position >= packsStartRow && position < packsEndRow) {
            showPackMenu(packs.get(position - packsStartRow));
            return true;
        }
        return false;
    }

    private void updateRowsAndNotify() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    /** Меню пака: точечное редактирование, поделиться, удалить. */
    private void showPackMenu(IconPack pack) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        // Приоритет показываем только у активных паков и только когда их
        // больше одного: у единственного двигать нечего, у выключенного
        // приоритета нет вовсе.
        final int priority = IconPacksConfig.getActivePackIds().indexOf(pack.getId());
        final int activeCount = IconPacksConfig.getActivePackIds().size();
        final boolean canRaise = priority > 0;
        final boolean canLower = priority >= 0 && priority < activeCount - 1;

        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (canRaise) {
            items.add(getString(R.string.IconPackPriorityUp));
            actions.add(() -> movePriority(pack, -1));
        }
        if (canLower) {
            items.add(getString(R.string.IconPackPriorityDown));
            actions.add(() -> movePriority(pack, 1));
        }
        items.add(getString(R.string.IconPackRename));
        actions.add(() -> showPackInfoSheet(pack));
        items.add(getString(R.string.Edit));
        actions.add(() -> openEditor(pack));
        items.add(getString(R.string.IconPackEditIcons));
        actions.add(() -> startEditing(pack));
        items.add(getString(R.string.ShareFile));
        actions.add(() -> sharePack(pack));
        items.add(getString(R.string.Delete));
        actions.add(() -> showDeleteDialog(pack));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(pack.getName());
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which >= 0 && which < actions.size()) {
                actions.get(which).run();
            }
        });
        showDialog(builder.create());
    }

    /**
     * Сдвинуть пак в списке приоритета.
     *
     * Порядок активных паков решает, чья иконка победит при совпадении имён:
     * берётся первая найденная (IconPackManager). exteraGram двигает паки
     * перетаскиванием за ручку (IconPacksActivity: allowReorder +
     * setReorderHandleOnly), но там строка пака — своя ячейка с ручкой, а у нас
     * обычный TextCheckCell с переключателем; отдельная ячейка с превью — своя
     * задача, до неё двигаем через меню.
     */
    private void movePriority(IconPack pack, int delta) {
        List<String> order = IconPacksConfig.getActivePackIds();
        int index = order.indexOf(pack.getId());
        int target = index + delta;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        Collections.swap(order, index, target);
        IconPacksConfig.setActivePackIds(order);
        IconPackManager.getInstance().reload();
        updateRowsAndNotify();
        showRestartHint();
    }

    /**
     * Включает режим точечной замены: пак становится «редактируемым», и поверх интерфейса
     * появляется плавающий пикер, показывающий иконки текущего экрана.
     */
    private void startEditing(IconPack pack) {
        if (!IconPacksConfig.enabled()) {
            IconPacksConfig.enabled.setConfigBool(true);
        }
        if (!IconPacksConfig.isPackActive(pack.getId())) {
            IconPacksConfig.togglePack(pack.getId());
        }
        IconPacksConfig.setEditingPackId(pack.getId());
        IconPackManager.getInstance().reload();
        updateRowsAndNotify();
        finishFragment();
        // подсказку показываем уже на экране, куда вернулись, иначе она уедет вместе с настройками
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.ui.ActionBar.BaseFragment target = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            if (target != null) {
                BulletinFactory.of(target)
                        .createSimpleBulletin(R.raw.info, getString(R.string.IconPackEditIconsHint))
                        .show();
            }
        }, 350);
    }

    /**
     * Экран со списком всех иконок пака (порт IconPacksActivity.java:409): открывается
     * вместе с включением режима точечной замены, как в exteraGram.
     */
    private void openEditor(IconPack pack) {
        if (!IconPacksConfig.enabled()) {
            IconPacksConfig.enabled.setConfigBool(true);
        }
        if (!IconPacksConfig.isPackActive(pack.getId())) {
            IconPacksConfig.togglePack(pack.getId());
        }
        IconPacksConfig.setEditingPackId(pack.getId());
        IconPackManager.getInstance().reload();
        if (getParentActivity() instanceof org.telegram.ui.LaunchActivity) {
            app.exteraless.icons.picker.IconPickerController.setActive(
                    (org.telegram.ui.LaunchActivity) getParentActivity(), true);
        }
        presentFragment(new IconPacksEditorActivity(pack.getId()));
    }

    private void sharePack(IconPack pack) {
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            File bundled = IconPackStorage.bundlePack(pack.getId());
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Exception ignore) {
                }
                if (bundled == null || getParentActivity() == null) {
                    return;
                }
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(android.content.Intent.EXTRA_STREAM,
                            androidx.core.content.FileProvider.getUriForFile(getParentActivity(),
                                    org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", bundled));
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getParentActivity().startActivity(android.content.Intent.createChooser(intent, getString(R.string.ShareFile)));
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            });
        });
    }

    /**
     * Создание пака и правка его метаданных — одна и та же шторка
     * (порт NewIconPackBottomSheet): без пака она создаёт новый и уводит в редактор,
     * с паком — переписывает имя, автора и версию.
     */
    private void showPackInfoSheet(IconPack pack) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        NewIconPackBottomSheet sheet = new NewIconPackBottomSheet(this, context, pack);
        sheet.setOnDone(() -> {
            IconPackManager.getInstance().reload();
            updateRowsAndNotify();
        });
        showDialog(sheet);
    }

    private void showRestartHint() {
        if (getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info, getString(R.string.IconPackRestartHint))
                .show();
    }

    private void showDeleteDialog(IconPack pack) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.IconPackDelete));
        builder.setMessage(LocaleController.formatString(R.string.IconPackDeleteInfo, pack.getName()));
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            IconPackManager.getInstance().deletePack(pack.getId());
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void openArchivePicker() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                    && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
        } catch (Throwable ignore) {
        }

        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                if (files.isEmpty()) {
                    return;
                }
                installArchive(new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        presentFragment(fragment);
    }

    private void installArchive(File file) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        IconPackManager.getInstance().installPack(file, error -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (error != null) {
                if (getParentActivity() != null) {
                    BulletinFactory.of(IconPacksActivity.this)
                            .createErrorBulletin(error.getLocalizedMessage())
                            .show();
                }
                return;
            }
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (getParentActivity() != null) {
                BulletinFactory.of(IconPacksActivity.this)
                        .createSimpleBulletin(R.raw.done, getString(R.string.IconPackInstalledToast))
                        .show();
            }
        });
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == basePacksHeaderRow) {
                        cell.setText(getString(R.string.IconReplacements));
                    } else if (position == enableHeaderRow) {
                        cell.setText(getString(R.string.IconPacks));
                    } else if (position == packsHeaderRow) {
                        cell.setText(getString(R.string.IconPackInstalledHeader));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == enableRow) {
                        cell.setTextAndCheck(getString(R.string.IconPackEnable),
                                IconPacksConfig.enabled.Bool(), false);
                    } else if (packsStartRow != -1 && position >= packsStartRow && position < packsEndRow) {
                        IconPack pack = packs.get(position - packsStartRow);
                        cell.setTextAndValueAndCheck(pack.getName(), buildPackSubtitle(pack),
                                IconPacksConfig.isPackActive(pack.getId()), true, true);
                    }
                    break;
                }
                case TYPE_RADIO: {
                    org.telegram.ui.Cells.TextRadioCell cell = (org.telegram.ui.Cells.TextRadioCell) holder.itemView;
                    int type = position == baseSolarRow ? BaseIconPacks.BASE_SOLAR
                            : position == baseRemixRow ? BaseIconPacks.BASE_REMIX
                            : position == baseHalloweenRow ? BaseIconPacks.BASE_HALLOWEEN
                            : BaseIconPacks.BASE_DEFAULT;
                    boolean isLast = baseHalloweenRow != -1 ? position == baseHalloweenRow : position == baseRemixRow;
                    cell.setTextAndValueAndCheck(BaseIconPacks.getName(type), BaseIconPacks.getAuthor(type),
                            BaseIconPacks.getSelected() == type, false, !isLast);
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == installRow) {
                        cell.setTextAndIcon(getString(R.string.IconPackInstall), R.drawable.msg_add, true);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    } else if (position == createRow) {
                        cell.setTextAndIcon(getString(R.string.IconPackCreate), R.drawable.msg_photoeditor, false);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    } else if (editingRow != -1 && position == editingRow) {
                        cell.setTextAndIcon(getString(R.string.IconPickerFinish), R.drawable.msg_check, false);
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == enableDividerRow) {
                        cell.setText(getString(R.string.IconPacksInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == packsDividerRow) {
                        cell.setText(getString(R.string.IconPacksHint));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == basePacksHeaderRow || position == enableHeaderRow || position == packsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == baseDefaultRow || position == baseSolarRow || position == baseRemixRow || position == baseHalloweenRow) {
                return TYPE_RADIO;
            } else if (position == basePacksDividerRow) {
                return TYPE_SHADOW;
            } else if (position == enableDividerRow || position == packsDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == installRow || position == createRow
                    || (editingRow != -1 && position == editingRow)) {
                return TYPE_TEXT;
            } else if (editingDividerRow != -1 && position == editingDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_CHECK;
        }
    }

    private String buildPackSubtitle(IconPack pack) {
        String count = LocaleController.formatPluralString("IconPackIconCount", pack.getIconCount());
        if (TextUtils.isEmpty(pack.getAuthor())) {
            return count;
        }
        return pack.getAuthor() + ", " + count;
    }
}
